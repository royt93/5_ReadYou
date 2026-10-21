package com.mckimquyen.reader.domain.repository

import android.content.Context
import android.database.sqlite.SQLiteBlobTooBigException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.ReadNewsController
import com.mckimquyen.reader.domain.sv.ReadNewsOutcome
import com.mckimquyen.reader.domain.sv.ReadNewsSpeaker
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/** DB riêng trong RAM; không đụng dữ liệu RSS của người dùng hoặc chạy mạng/TTS thật. */
@RunWith(AndroidJUnit4::class)
class ArticleListCursorWindowInstrumentationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    // @Before tạo DB trước mỗi test, @After đóng sau khi test kết thúc.
    private lateinit var db: AndroidDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).build()
        db.accountDao().insert(Account(id = ACCOUNT, name = "Test", type = AccountType.Local))
        db.groupDao().insert(Group(id = GROUP, name = "Group", accountId = ACCOUNT))
        db.feedDao().insert(Feed(id = FEED, name = "Feed", url = "https://example.com/rss", groupId = GROUP, accountId = ACCOUNT))
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun seed(raw: String, full: String?) {
        db.articleDao().insert(Article(
            id = ARTICLE, title = "Huge article", date = Date(), rawDescription = raw, fullContent = full,
            shortDescription = "Summary", link = "https://example.com/article", feedId = FEED, accountId = ACCOUNT,
        ))
    }

    @Test fun unsafeControlThrows_butLightweightListsStayReadable() = runBlocking {
        seed("x".repeat(OVERSIZED_BODY_CHARS), null)
        val sql = db.openHelper.readableDatabase
        try {
            sql.query("SELECT * FROM article").use { it.moveToFirst() }
            fail("Control SELECT * must exceed CursorWindow")
        } catch (_: SQLiteBlobTooBigException) {
            // Xác nhận fixture đủ lớn để bắt đúng regression trên Android thật.
        }
        assertEquals(listOf(ARTICLE), db.articleDao().queryUnreadArticleList(ACCOUNT, LIMIT).map { it.id })
        assertEquals(listOf(ARTICLE), db.articleDao().queryRecentArticleListWithFeed(ACCOUNT, LIMIT).map { it.article.id })
    }

    @Test fun chunkedBody_preservesOversizedUnicodeAndAccountScope() = runBlocking {
        val text = "漢🙂é".repeat(UNICODE_REPEATS)
        seed("Raw", text)
        assertEquals(text, db.articleDao().queryArticleBody(ACCOUNT, ARTICLE)?.fullContent)
        assertEquals(null, db.articleDao().queryArticleBody(OTHER_ACCOUNT, ARTICLE))
        assertEquals(null, db.articleDao().queryArticleBody(ACCOUNT, "missing"))
    }

    @Test fun readNews_readsFullBodyPastCursorWindowWithoutNetwork() = runBlocking {
        val text = "word ".repeat(WORD_REPEATS) + " END_MARKER"
        seed("Short teaser", "<p>$text</p>")
        var spoken: String? = null
        val speaker = object : ReadNewsSpeaker {
            override val completions = MutableSharedFlow<Unit>()
            override fun play(text: String, title: String, subtitle: String) { spoken = text }
            override fun speakNotice(text: String) = Unit
            override fun stop() = Unit
        }
        val controller = ReadNewsController(context, db.articleDao(), speaker, Dispatchers.IO, Dispatchers.Default, scope)
        assertEquals(ReadNewsOutcome.STARTED, controller.start())
        val actual = requireNotNull(spoken)
        assertTrue(actual.endsWith("END_MARKER"))
        assertEquals(text.trim(), actual)
    }

    private companion object {
        const val ACCOUNT = 1
        const val OTHER_ACCOUNT = 2
        const val GROUP = "cursor-group"
        const val FEED = "cursor-feed"
        const val ARTICLE = "cursor-article"
        const val LIMIT = 20
        const val OVERSIZED_BODY_CHARS = 3 * 1024 * 1024
        const val UNICODE_REPEATS = 500_000
        const val WORD_REPEATS = 500_000
    }
}
