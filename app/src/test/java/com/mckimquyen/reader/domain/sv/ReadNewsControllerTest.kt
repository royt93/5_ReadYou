package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadNewsControllerTest {

    private class FakeSpeaker : ReadNewsSpeaker {
        val completionsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        override val completions = completionsFlow
        data class Played(val text: String, val title: String, val subtitle: String)
        val played = mutableListOf<Played>()
        val notices = mutableListOf<String>()
        var stopped = 0
        override fun play(text: String, title: String, subtitle: String) { played += Played(text, title, subtitle) }
        override fun speakNotice(text: String) { notices += text }
        override fun stop() { stopped++ }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun article(id: String, raw: String = "<p>Body $id</p>", full: String? = null, title: String = "Title $id") = Article(
        id = id, date = Date(), title = title, rawDescription = raw, shortDescription = "", fullContent = full,
        link = "https://example.com/$id", feedId = "feed-1", accountId = 1,
    )

    private fun withFeed(a: Article) = ArticleWithFeed(a, Feed(id = "feed-1", name = "BBC", url = "https://x", groupId = "g", accountId = 1))

    private fun TestScope.build(
        dao: ArticleDao,
        speaker: FakeSpeaker,
    ): ReadNewsController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // backgroundScope của runTest không chạy cùng dispatcher này nên collector completions sẽ không bao giờ
        // nhận sự kiện khi test gọi advanceUntilIdle(); dùng scope riêng trên cùng StandardTestDispatcher.
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ReadNewsController(context, dao, speaker, dispatcher, dispatcher, scope)
        testScheduler.runCurrent() // để collector trong init đăng ký trước khi test phát sự kiện
        return controller
    }

    private fun daoWith(vararg articles: Article): ArticleDao {
        val dao = mockk<ArticleDao>(relaxed = true)
        coEvery { dao.queryLatestUnread(any(), any()) } returns articles.toList()
        articles.forEach { coEvery { dao.queryById(it.id) } returns withFeed(it) }
        return dao
    }

    @Test
    fun start_withNoUnread_returnsNoUnreadAndSpeaksNotice() = runTest {
        val speaker = FakeSpeaker()
        val controller = build(daoWith(), speaker)

        val outcome = controller.start()

        assertEquals(ReadNewsOutcome.NO_UNREAD, outcome)
        assertEquals(listOf("No new articles"), speaker.notices)
        assertTrue(speaker.played.isEmpty())
        assertFalse(controller.hasQueue.value)
    }

    @Test
    fun start_playsFirstArticleWithPlainTextTitleAndFeedName() = runTest {
        val speaker = FakeSpeaker()
        val controller = build(daoWith(article("a1", full = "<p>Hello <b>world</b></p>"), article("a2")), speaker)

        val outcome = controller.start()

        assertEquals(ReadNewsOutcome.STARTED, outcome)
        val played = speaker.played.single()
        assertTrue("văn bản phải đã bỏ thẻ HTML: ${played.text}", played.text.contains("Hello world") && !played.text.contains("<"))
        assertEquals("Title a1", played.title)
        assertEquals("BBC", played.subtitle)
        assertTrue(controller.hasQueue.value)
    }

    @Test
    fun start_fallsBackToRawDescriptionWhenNoFullContent() = runTest {
        val speaker = FakeSpeaker()
        val controller = build(daoWith(article("a1", raw = "<p>Raw body</p>", full = null)), speaker)

        controller.start()

        assertTrue(speaker.played.single().text.contains("Raw body"))
    }

    @Test
    fun start_skipsBlankArticleAndPlaysNext() = runTest {
        val speaker = FakeSpeaker()
        val controller = build(daoWith(article("blank", raw = "<p>   </p>"), article("a2")), speaker)

        val outcome = controller.start()

        assertEquals(ReadNewsOutcome.STARTED, outcome)
        assertEquals("Title a2", speaker.played.single().title)
    }

    @Test
    fun start_allBlank_returnsNoReadableText() = runTest {
        val speaker = FakeSpeaker()
        val controller = build(daoWith(article("b1", raw = "<p></p>"), article("b2", raw = " ")), speaker)

        assertEquals(ReadNewsOutcome.NO_READABLE_TEXT, controller.start())
        assertTrue(speaker.played.isEmpty())
    }

    @Test
    fun start_requestsAtMostQueueLimitForCurrentAccount() = runTest {
        val dao = daoWith(article("a1"))
        build(dao, FakeSpeaker()).start()

        coVerify(exactly = 1) { dao.queryLatestUnread(1, ReadNewsController.READ_NEWS_QUEUE_LIMIT) }
    }

    @Test
    fun completion_marksCurrentArticleReadAndKeepsQueue() = runTest {
        val dao = daoWith(article("a1"), article("a2"))
        val speaker = FakeSpeaker()
        val controller = build(dao, speaker)
        controller.start()
        advanceUntilIdle()

        speaker.completionsFlow.emit(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { dao.markAsReadByArticleId(1, "a1", false) }
        assertEquals("không tự đọc bài kế", 1, speaker.played.size)
        assertTrue(controller.hasQueue.value)
    }

    @Test
    fun next_afterCompletion_playsNextWithoutMarkingTwice() = runTest {
        val dao = daoWith(article("a1"), article("a2"))
        val speaker = FakeSpeaker()
        val controller = build(dao, speaker)
        controller.start()
        advanceUntilIdle()
        speaker.completionsFlow.emit(Unit)
        advanceUntilIdle()

        val outcome = controller.next()

        assertEquals(ReadNewsOutcome.STARTED, outcome)
        assertEquals(listOf("Title a1", "Title a2"), speaker.played.map { it.title })
        coVerify(exactly = 1) { dao.markAsReadByArticleId(1, "a1", false) }
    }

    @Test
    fun next_withoutCompletion_doesNotMarkCurrentRead() = runTest {
        val dao = daoWith(article("a1"), article("a2"))
        val speaker = FakeSpeaker()
        val controller = build(dao, speaker)
        controller.start()
        advanceUntilIdle()

        controller.next()

        coVerify(exactly = 0) { dao.markAsReadByArticleId(1, "a1", any()) }
        assertEquals("Title a2", speaker.played.last().title)
    }

    @Test
    fun next_atEndOfQueue_returnsQueueFinishedSpeaksNoticeAndClearsQueue() = runTest {
        val dao = daoWith(article("a1"))
        val speaker = FakeSpeaker()
        val controller = build(dao, speaker)
        controller.start()
        advanceUntilIdle()
        speaker.completionsFlow.emit(Unit)
        advanceUntilIdle()

        val outcome = controller.next()

        assertEquals(ReadNewsOutcome.QUEUE_FINISHED, outcome)
        assertEquals(listOf("No more new articles"), speaker.notices)
        assertFalse(controller.hasQueue.value)
    }

    @Test
    fun next_withoutQueue_returnsQueueFinished() = runTest {
        val controller = build(daoWith(), FakeSpeaker())

        assertEquals(ReadNewsOutcome.QUEUE_FINISHED, controller.next())
    }

    @Test
    fun start_whileQueueActive_replacesIt() = runTest {
        val dao = daoWith(article("a1"), article("a2"))
        val speaker = FakeSpeaker()
        val controller = build(dao, speaker)
        controller.start()
        controller.next()

        controller.start()

        assertEquals(listOf("Title a1", "Title a2", "Title a1"), speaker.played.map { it.title })
        coVerify(exactly = 2) { dao.queryLatestUnread(any(), any()) }
    }

    @Test
    fun start_whenDaoThrows_returnsErrorAndDoesNotCrash() = runTest {
        val dao = mockk<ArticleDao>(relaxed = true)
        coEvery { dao.queryLatestUnread(any(), any()) } throws IOException("disk")
        val controller = build(dao, FakeSpeaker())

        assertEquals(ReadNewsOutcome.ERROR, controller.start())
    }

    @Test
    fun start_rethrowsCancellation() = runTest {
        val dao = mockk<ArticleDao>(relaxed = true)
        coEvery { dao.queryLatestUnread(any(), any()) } throws CancellationException("cancelled")
        val controller = build(dao, FakeSpeaker())

        var thrown: Throwable? = null
        try { controller.start() } catch (e: CancellationException) { thrown = e }

        assertTrue("CancellationException không được nuốt", thrown != null)
    }

    @Test
    fun completion_markFailure_isLoggedNotThrown() = runTest {
        val dao = daoWith(article("a1"), article("a2"))
        coEvery { dao.markAsReadByArticleId(any(), any(), any()) } throws IOException("write failed")
        val speaker = FakeSpeaker()
        val controller = build(dao, speaker)
        controller.start()
        advanceUntilIdle()

        speaker.completionsFlow.emit(Unit)
        advanceUntilIdle()
        val outcome = controller.next()

        assertEquals(ReadNewsOutcome.STARTED, outcome)
        assertEquals("Title a2", speaker.played.last().title)
    }
}
