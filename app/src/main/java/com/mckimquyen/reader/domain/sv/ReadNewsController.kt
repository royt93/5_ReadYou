package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.util.Log
import androidx.core.text.HtmlCompat
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Đọc tin chưa đọc rảnh tay (Assistant / shortcut / deep link). Giữ một hàng đợi tối đa
 * [READ_NEWS_QUEUE_LIMIT] bài trong bộ nhớ tiến trình. Không bao giờ tự đọc bài kế: sau khi một bài đọc xong
 * nó được đánh dấu đã đọc và controller chờ [next] (nút Next trên thông báo media).
 */
@Singleton
class ReadNewsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val speaker: ReadNewsSpeaker,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private val lock = Mutex()
    private var queue: List<String> = emptyList()
    private var position: Int = NO_POSITION
    private var currentMarkedRead: Boolean = false

    private val _hasQueue = MutableStateFlow(false)

    /** true khi còn một bài kế tiếp để đọc, dùng để hiện nút Next. */
    val hasQueue: StateFlow<Boolean> = _hasQueue.asStateFlow()

    init {
        applicationScope.launch { speaker.completions.collect { onCompletionObserved() } }
    }

    suspend fun start(): ReadNewsOutcome = lock.withLock {
        try {
            val accountId = context.currentAccountId
            val unread = withContext(ioDispatcher) { articleDao.queryUnreadArticleList(accountId, READ_NEWS_QUEUE_LIMIT) }
            if (unread.isEmpty()) {
                clearQueue()
                speaker.speakNotice(context.getString(R.string.read_news_no_unread))
                return@withLock ReadNewsOutcome.NO_UNREAD
            }
            queue = unread.map { it.id }
            position = NO_POSITION
            val started = playFrom(0)
            if (!started) {
                clearQueue()
                return@withLock ReadNewsOutcome.NO_READABLE_TEXT
            }
            ReadNewsOutcome.STARTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            ReadNewsOutcome.ERROR
        }
    }

    suspend fun next(): ReadNewsOutcome = lock.withLock {
        if (queue.isEmpty()) return@withLock ReadNewsOutcome.QUEUE_FINISHED
        try {
            if (playFrom(position + 1)) return@withLock ReadNewsOutcome.STARTED
            clearQueue()
            speaker.speakNotice(context.getString(R.string.read_news_queue_finished))
            ReadNewsOutcome.QUEUE_FINISHED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "next failed", e)
            ReadNewsOutcome.ERROR
        }
    }

    /** Phát bài đọc được đầu tiên từ [from]; false nếu không còn bài nào có nội dung. */
    private suspend fun playFrom(from: Int): Boolean {
        for (index in from until queue.size) {
            val accountId = context.currentAccountId
            val item = withContext(ioDispatcher) { articleDao.queryArticleListWithFeedById(accountId, queue[index]) } ?: continue
            val body = withContext(ioDispatcher) { articleDao.queryArticleBody(accountId, queue[index]) } ?: continue
            val html = body.fullContent ?: body.rawDescription
            val text = withContext(defaultDispatcher) { plainText(html) }
            if (text.isBlank()) continue
            position = index
            currentMarkedRead = false
            speaker.play(text, item.article.title, item.feed.name)
            _hasQueue.value = index < queue.lastIndex
            return true
        }
        return false
    }

    private suspend fun onCompletionObserved() = lock.withLock {
        val id = queue.getOrNull(position) ?: return@withLock
        if (currentMarkedRead) return@withLock
        currentMarkedRead = true
        try {
            val accountId = context.currentAccountId
            withContext(ioDispatcher) { articleDao.markAsReadByArticleId(accountId, id, false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "mark read failed for $id", e)
        }
    }

    private fun clearQueue() {
        queue = emptyList()
        position = NO_POSITION
        currentMarkedRead = false
        _hasQueue.value = false
    }

    private fun plainText(html: String): String =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            .replace(WHITESPACE_RUN, " ")
            .trim()

    companion object {
        const val READ_NEWS_QUEUE_LIMIT = 10
        private const val NO_POSITION = -1
        private const val TAG = "ReadNewsController"
        private val WHITESPACE_RUN = Regex("[\\s\\u00A0]+")
    }
}
