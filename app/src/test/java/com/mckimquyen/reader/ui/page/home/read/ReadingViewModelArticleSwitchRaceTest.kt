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
 * Regression coverage for FIX-10: a slow AI request (Summary / Mind Map / Deep Read) started
 * for one article must never leak its result into the UI state once the user has switched to
 * a different article.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelArticleSwitchRaceTest {

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
    fun requestSummary_lateResultForPreviousArticle_doesNotOverwriteNewArticleState() = runTest(testDispatcher) {
        val highlightsForA = ArticleHighlights(
            tldr = "Summary of A",
            keyTakeaways = listOf("A1"),
            readingTimeSavedMin = 1,
            tags = emptyList(),
            isOfflineFallback = false,
        )
        coEvery { summaryService.extractHighlights(any(), any(), any()) } coAnswers {
            delay(1_000)
            highlightsForA
        }

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        // User opens Summary for article A (slow network -> response only lands after 1000ms).
        viewModel.requestSummary()
        assertTrue(viewModel.readingUiState.value.summaryState is SummaryState.Loading)

        // Before the response arrives, the user switches to article B.
        viewModel.initData("art_race_b")
        advanceUntilIdle()

        // Article B never requested a summary -> state must be Idle, and the stale
        // response for A (now available) must not leak in once its delay elapses.
        assertEquals(SummaryState.Idle, viewModel.readingUiState.value.summaryState)
        advanceTimeBy(1_500)
        advanceUntilIdle()
        assertEquals(SummaryState.Idle, viewModel.readingUiState.value.summaryState)
        assertEquals("art_race_b", viewModel.readingUiState.value.articleWithFeed?.article?.id)
    }

    @Test
    fun requestSummary_rapidDoubleRequestSameArticle_cancelledFirstCallDoesNotClobberSecondResult() = runTest(testDispatcher) {
        val firstHighlights = ArticleHighlights("first", listOf("x"), 1, emptyList(), false)
        val secondHighlights = ArticleHighlights("second", listOf("y"), 1, emptyList(), false)
        coEvery { summaryService.extractHighlights(any(), any(), any()) } coAnswers {
            delay(500)
            firstHighlights
        } andThenAnswer {
            secondHighlights
        }

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        viewModel.requestSummary() // first call, will be cancelled before its delay elapses
        advanceTimeBy(100)
        viewModel.requestSummary() // second call supersedes the first
        advanceUntilIdle()

        val state = viewModel.readingUiState.value.summaryState
        assertTrue(state is SummaryState.Success)
        assertEquals(secondHighlights, (state as SummaryState.Success).highlights)
    }

    @Test
    fun requestMindMap_lateResultForPreviousArticle_doesNotOverwriteNewArticleState() = runTest(testDispatcher) {
        val mindMapForA = ArticleMindMap(rootTitle = "A", nodes = emptyList(), isOfflineFallback = false)
        coEvery { summaryService.generateMindMap(any(), any(), any()) } coAnswers {
            delay(1_000)
            mindMapForA
        }

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        viewModel.requestMindMap()
        assertTrue(viewModel.readingUiState.value.mindMapState is MindMapState.Loading)

        viewModel.initData("art_race_b")
        advanceUntilIdle()

        assertEquals(MindMapState.Idle, viewModel.readingUiState.value.mindMapState)
        advanceTimeBy(1_500)
        advanceUntilIdle()
        assertEquals(MindMapState.Idle, viewModel.readingUiState.value.mindMapState)
    }

    @Test
    fun sendDeepReadQuestion_lateReplyForPreviousArticle_doesNotAppearAfterSwitchingArticle() = runTest(testDispatcher) {
        val replyForA = DeepReadMessage(sender = DeepReadSender.ASSISTANT, content = "Reply for A")
        coEvery { summaryService.askArticleQuestion(any(), any(), any(), any(), any()) } coAnswers {
            delay(1_000)
            replyForA
        }

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        viewModel.openDeepRead()
        viewModel.sendDeepReadQuestion("What is this about?")

        // Switch article before the reply for A arrives.
        viewModel.initData("art_race_b")
        advanceUntilIdle()
        assertEquals(DeepReadState.Idle, viewModel.readingUiState.value.deepReadState)

        advanceTimeBy(1_500)
        advanceUntilIdle()

        // Must stay Idle for article B: the stale reply for A's session must never resurrect it.
        assertEquals(DeepReadState.Idle, viewModel.readingUiState.value.deepReadState)
    }

    @Test
    fun initData_switchingArticle_resetsAiStatesToIdle() = runTest(testDispatcher) {
        coEvery { summaryService.extractHighlights(any(), any(), any()) } returns
            ArticleHighlights("t", listOf("a"), 1, emptyList(), false)

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        viewModel.requestSummary()
        advanceUntilIdle()
        assertTrue(viewModel.readingUiState.value.summaryState is SummaryState.Success)

        viewModel.initData("art_race_b")
        advanceUntilIdle()

        assertEquals(SummaryState.Idle, viewModel.readingUiState.value.summaryState)
        assertEquals(MindMapState.Idle, viewModel.readingUiState.value.mindMapState)
        assertEquals(DeepReadState.Idle, viewModel.readingUiState.value.deepReadState)
    }

    @Test
    fun initData_reopeningSameArticle_doesNotResetAlreadyLoadedSummary() = runTest(testDispatcher) {
        coEvery { summaryService.extractHighlights(any(), any(), any()) } returns
            ArticleHighlights("t", listOf("a"), 1, emptyList(), false)

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_race_a")
        advanceUntilIdle()
        viewModel.requestSummary()
        advanceUntilIdle()
        assertTrue(viewModel.readingUiState.value.summaryState is SummaryState.Success)

        // Re-entering the SAME article (e.g. process/config change) must not wipe an
        // already-fetched summary.
        viewModel.initData("art_race_a")
        advanceUntilIdle()

        assertFalse(viewModel.readingUiState.value.summaryState == SummaryState.Idle)
    }
}
