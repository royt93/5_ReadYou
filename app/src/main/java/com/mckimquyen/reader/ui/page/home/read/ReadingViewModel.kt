package com.mckimquyen.reader.ui.page.home.read

import android.util.Log
import androidx.annotation.Keep
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ItemSnapshotList
import com.mckimquyen.reader.domain.model.article.ArticleFlowItem
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val rssService: RssSv,
    private val rssHelper: RssHelper,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _readingUiState = MutableStateFlow(ReadingUiState())
    val readingUiState: StateFlow<ReadingUiState> = _readingUiState.asStateFlow()

    private var fetchJob: kotlinx.coroutines.Job? = null

    init {
        // Collect TTS state once per ViewModel lifetime.
        // Previously this was inside initData(), which created a new collector
        // on every article open — causing duplicate event processing over time.
        viewModelScope.launch {
            ttsManager.ttsState.collect { state ->
                _readingUiState.update { it.copy(ttsState = state) }
            }
        }
    }

    fun initData(articleId: String, autoTtsEnabled: Boolean = false) {
        // Capture current TTS state before switching feed
        val wasPlaying = _readingUiState.value.ttsState == TtsState.PLAYING
        val shouldAutoPlay = autoTtsEnabled || wasPlaying
        
        if (wasPlaying) {
            Log.d("roy93~", "ReadingViewModel: Switching feed, stopping current TTS")
            ttsManager.stop()
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            showLoading()
            _readingUiState.update {
                it.copy(articleWithFeed = rssService.get().findArticleById(articleId))
            }
            _readingUiState.value.articleWithFeed?.let {
                if (it.feed.isFullContent) internalRenderFullContent(shouldAutoPlay)
                else renderDescriptionContent(shouldAutoPlay)
            }
            // java.lang.NullPointerException: Attempt to invoke virtual method
            // 'boolean androidx.compose.ui.node.LayoutNode.getNeedsOnPositionedDispatch$ui_release()'
            // on a null object reference
            if (_readingUiState.value.listState.firstVisibleItemIndex != 0) {
                _readingUiState.value.listState.scrollToItem(0)
            }
            hideLoading()
        }
    }

    fun renderDescriptionContent(autoPlay: Boolean = false) {
        _readingUiState.update {
            it.copy(
                content = it.articleWithFeed?.article?.fullContent
                    ?: it.articleWithFeed?.article?.rawDescription ?: "",
                isFullContent = false
            )
        }
        if (autoPlay) {
            playCurrentContent()
        }
    }

    fun renderFullContent() {
        val wasPlaying = _readingUiState.value.ttsState == TtsState.PLAYING
        if (wasPlaying) {
            ttsManager.stop()
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            internalRenderFullContent(wasPlaying)
        }
    }

    private suspend fun internalRenderFullContent(autoPlay: Boolean = false) {
        showLoading()
        try {
            _readingUiState.update {
                it.copy(
                    content = rssHelper.parseFullContent(
                        _readingUiState.value.articleWithFeed?.article?.link ?: "",
                        _readingUiState.value.articleWithFeed?.article?.title ?: ""
                    ),
                    isFullContent = true
                )
            }
            if (autoPlay) {
                playCurrentContent()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i("RLog", "renderFullContent: ${e.message}")
            _readingUiState.update { it.copy(content = e.message) }
        }
        hideLoading()
    }

    private fun playCurrentContent() {
        val content = _readingUiState.value.content
        if (content == null) {
            Log.d("roy93~", "ReadingViewModel content is null, cannot play")
            return
        }
        Log.d("roy93~", "ReadingViewModel parsing HTML to plain text")
        // Use Regex to remove HTML tags or parse it
        val plainText = androidx.core.text.HtmlCompat.fromHtml(
            content, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
        Log.d("roy93~", "ReadingViewModel starting TTS play")
        ttsManager.play(plainText)
    }

    fun togglePlayAudio() {
        Log.d("roy93~", "ReadingViewModel togglePlayAudio called. Current state: ${_readingUiState.value.ttsState}")
        if (_readingUiState.value.ttsState == TtsState.PLAYING) {
            Log.d("roy93~", "ReadingViewModel stopping TTS")
            ttsManager.stop()
        } else {
            playCurrentContent()
        }
    }

    override fun onCleared() {
        Log.d("roy93~", "ReadingViewModel onCleared: stopping TTS")
        ttsManager.stop()
        super.onCleared()
    }

    fun markUnread(isUnread: Boolean) {
        val articleWithFeed = _readingUiState.value.articleWithFeed ?: return
        // Capture articleId before the coroutine launch to avoid a race condition
        // where articleWithFeed could be set to null by initData() before line 138 runs.
        val articleId = articleWithFeed.article.id
        viewModelScope.launch {
            _readingUiState.update {
                it.copy(
                    articleWithFeed = articleWithFeed.copy(
                        article = articleWithFeed.article.copy(
                            isUnread = isUnread
                        )
                    )
                )
            }
            rssService.get().markAsRead(
                groupId = null,
                feedId = null,
                articleId = articleId,
                before = null,
                isUnread = isUnread,
            )
        }
    }

    fun markStarred(isStarred: Boolean) {
        val articleWithFeed = _readingUiState.value.articleWithFeed ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _readingUiState.update {
                it.copy(
                    articleWithFeed = articleWithFeed.copy(
                        article = articleWithFeed.article.copy(
                            isStarred = isStarred
                        )
                    )
                )
            }
            rssService.get().markAsStarred(
                articleId = articleWithFeed.article.id,
                isStarred = isStarred,
            )
        }
    }

    private fun showLoading() {
        _readingUiState.update {
            it.copy(isLoading = true)
        }
    }

    private fun hideLoading() {
        _readingUiState.update {
            it.copy(isLoading = false)
        }
    }

    fun recorderNextArticle(pagingItems: ItemSnapshotList<ArticleFlowItem>) {
        if (pagingItems.size > 0) {
            val cur = _readingUiState.value.articleWithFeed?.article
            if (cur != null) {
                var found = false
                for (item in pagingItems) {
                    if (item is ArticleFlowItem.Article) {
                        val itemId = item.articleWithFeed.article.id
                        if (itemId == cur.id) {
                            found = true
                            _readingUiState.update {
                                it.copy(nextArticleId = "")
                            }
                        } else if (found) {
                            _readingUiState.update {
                                it.copy(nextArticleId = itemId)
                            }
                            break
                        }
                    }
                }
            }
        }
    }
}

@Keep
data class ReadingUiState(
    val articleWithFeed: ArticleWithFeed? = null,
    val content: String? = null,
    val isFullContent: Boolean = false,
    val isLoading: Boolean = true,
    val listState: LazyListState = LazyListState(),
    val nextArticleId: String = "",
    val ttsState: TtsState = TtsState.IDLE,
)
