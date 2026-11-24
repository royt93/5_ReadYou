package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * markStarred and markUnread captured the article on screen and wrote that whole object back when their coroutine ran, so starring A and tapping Next straight away put A's
 * title, feed and link over B. Reopening the mind map called Gemini again although a map was already on screen (openSummary already had that guard).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelStaleWriteTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)
    private val zenAudioManager = mockk<ZenAudioManager>(relaxed = true)

    private fun article(id: String, title: String) = Article(
        id = id,
        date = Date(),
        title = title,
        author = "Author",
        rawDescription = "<p>Content for $title.</p>",
        shortDescription = title,
        fullContent = "<p>Content for $title.</p>",
        link = "https://example.com/$id",
        feedId = "feed_1",
        accountId = 1,
        isUnread = false,
    )

    private val dummyFeed = Feed(
        id = "feed_1",
        name = "Feed",
        url = "https://example.com/feed",
        groupId = "group_1",
        accountId = 1,
        isFullContent = false,
    )

    private val articleA = article("art_race_a", "Article A")
    private val articleB = article("art_race_b", "Article B")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        io.mockk.mockkStatic(androidx.core.text.HtmlCompat::class)
        every { androidx.core.text.HtmlCompat.fromHtml(any(), any()) } answers {
            val html = firstArg<String>()
            val spanned = mockk<android.text.Spanned>()
            every { spanned.toString() } returns html.replace(Regex("<[^>]*>"), "")
            spanned
        }

        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(com.mckimquyen.reader.infrastructure.audio.TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
        coEvery { repo.findArticleById("art_race_a") } returns ArticleWithFeed(articleA, dummyFeed)
        coEvery { repo.findArticleById("art_race_b") } returns ArticleWithFeed(articleB, dummyFeed)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(androidx.core.text.HtmlCompat::class)
    }

    @Test
    fun markStarred_whoseIoWorkRunsAfterTheSwitch_doesNotPutTheOldArticleOnScreen() = runTest(testDispatcher) {
        // markStarred updates the screen from the IO dispatcher. When that thread is slow the update lands after the next article is already shown.
        // The IO dispatcher gets its own scheduler so the test decides which side runs first.
        val ioScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val slowIo = StandardTestDispatcher(ioScheduler)
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager, testDispatcher, ioDispatcher = slowIo)
        viewModel.initData("art_race_a")
        advanceUntilIdle()
        assertEquals("art_race_a", viewModel.readingUiState.value.articleWithFeed?.article?.id)

        viewModel.markStarred(true)        // queued on slowIo, not run yet
        viewModel.initData("art_race_b")
        advanceUntilIdle()                 // main work only: B is on screen
        assertEquals("art_race_b", viewModel.readingUiState.value.articleWithFeed?.article?.id)

        ioScheduler.advanceUntilIdle()     // now the delayed IO block runs

        val shown = viewModel.readingUiState.value.articleWithFeed?.article
        assertEquals("art_race_b", shown?.id)
        assertEquals("Article B", shown?.title)
        assertFalse("B was never starred", shown?.isStarred == true)
    }

    @Test
    fun markStarred_onTheSameArticle_stillUpdatesTheScreen() = runTest(testDispatcher) {
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager, testDispatcher, ioDispatcher = testDispatcher)
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        viewModel.markStarred(true)
        advanceUntilIdle()

        assertTrue(viewModel.readingUiState.value.articleWithFeed?.article?.isStarred == true)
    }

    @Test
    fun openMindMap_whenAMapIsAlreadyShown_doesNotCallGeminiAgain() = runTest(testDispatcher) {
        val map = ArticleMindMap(rootTitle = "A", nodes = emptyList(), isOfflineFallback = false)
        var calls = 0
        coEvery { summaryService.generateMindMap(any(), any(), any()) } coAnswers {
            calls++
            map
        }
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager, testDispatcher, ioDispatcher = testDispatcher)
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        viewModel.openMindMap()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(viewModel.readingUiState.value.mindMapState is MindMapState.Success)

        viewModel.dismissMindMap()
        viewModel.openMindMap()
        advanceUntilIdle()

        assertEquals("a second open must reuse the map", 1, calls)
        assertTrue(viewModel.readingUiState.value.mindMapState is MindMapState.Success)
    }

    @Test
    fun openMindMap_onANewArticle_asksAgain() = runTest(testDispatcher) {
        val map = ArticleMindMap(rootTitle = "x", nodes = emptyList(), isOfflineFallback = false)
        var calls = 0
        coEvery { summaryService.generateMindMap(any(), any(), any()) } coAnswers {
            calls++
            map
        }
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager, testDispatcher, ioDispatcher = testDispatcher)
        viewModel.initData("art_race_a")
        advanceUntilIdle()
        viewModel.openMindMap()
        advanceUntilIdle()

        viewModel.initData("art_race_b")
        advanceUntilIdle()
        viewModel.openMindMap()
        advanceUntilIdle()

        assertEquals(2, calls)
    }
}
