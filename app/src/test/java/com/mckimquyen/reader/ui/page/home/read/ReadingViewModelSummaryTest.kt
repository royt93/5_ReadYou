package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelSummaryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)
    private val zenAudioManager = mockk<ZenAudioManager>(relaxed = true)

    private val dummyArticle = Article(
        id = "art_sum_1",
        date = Date(),
        title = "AI in Android 17",
        author = "Google",
        rawDescription = "<p>Android 17 introduces system-wide intelligence and local micro-models.</p>",
        shortDescription = "Android 17 intelligence",
        fullContent = "<p>Android 17 introduces system-wide intelligence and local micro-models.</p>",
        link = "https://developer.android.com",
        feedId = "feed_1",
        accountId = 1,
        isUnread = false,
    )
    private val dummyFeed = Feed(
        id = "feed_1",
        name = "Android Developers",
        url = "https://android-developers.googleblog.com/feed",
        groupId = "group_1",
        accountId = 1,
        isFullContent = false
    )
    private val dummyArticleWithFeed = ArticleWithFeed(dummyArticle, dummyFeed)

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
        val mockSpanned = mockk<android.text.Spanned>()
        every { mockSpanned.toString() } returns "Plain text content about Android 17 and AI."
        every { androidx.core.text.HtmlCompat.fromHtml(any(), any()) } returns mockSpanned

        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(com.mckimquyen.reader.infrastructure.audio.TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
        coEvery { repo.findArticleById("art_sum_1") } returns dummyArticleWithFeed
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(androidx.core.text.HtmlCompat::class)
    }

    @Test
    fun openAndDismissSummary_updatesSheetVisibilityState() = runTest(testDispatcher) {
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_sum_1", autoTtsEnabled = false)
        advanceUntilIdle()

        assertFalse(viewModel.readingUiState.value.showSummarySheet)

        viewModel.openSummary()
        assertTrue(viewModel.readingUiState.value.showSummarySheet)

        viewModel.dismissSummary()
        assertFalse(viewModel.readingUiState.value.showSummarySheet)
    }

    @Test
    fun requestSummary_success_updatesSummaryStateWithHighlights() = runTest(testDispatcher) {
        val expectedHighlights = ArticleHighlights(
            tldr = "Android 17 revolutionizes on-device AI.",
            keyTakeaways = listOf("Micro-models run locally", "Zero latency"),
            readingTimeSavedMin = 2,
            tags = listOf("AI", "Android"),
            isOfflineFallback = false,
        )
        coEvery { summaryService.extractHighlights(any(), any(), any()) } returns expectedHighlights

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_sum_1", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.requestSummary()
        advanceUntilIdle()

        val state = viewModel.readingUiState.value.summaryState
        assertTrue(state is SummaryState.Success)
        val success = state as SummaryState.Success
        assertEquals(expectedHighlights, success.highlights)
        assertEquals(2, success.highlights.keyTakeaways.size)
        assertEquals("Android 17 revolutionizes on-device AI.", success.highlights.tldr)
        assertFalse(success.highlights.isOfflineFallback)
    }

    @Test
    fun requestSummary_forceOffline_generatesOfflineHighlights() = runTest(testDispatcher) {
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_sum_1", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.requestSummary(forceOffline = true)
        advanceUntilIdle()

        val state = viewModel.readingUiState.value.summaryState
        assertTrue(state is SummaryState.Success)
        val success = state as SummaryState.Success
        assertTrue(success.highlights.isOfflineFallback)
        assertTrue(success.highlights.keyTakeaways.isNotEmpty())
    }

    @Test
    fun requestSummary_emptyContent_emitsErrorState() = runTest(testDispatcher) {
        val emptySpanned = mockk<android.text.Spanned>()
        every { emptySpanned.toString() } returns ""
        every { androidx.core.text.HtmlCompat.fromHtml(any(), any()) } returns emptySpanned

        val emptyArticle = dummyArticleWithFeed.copy(
            article = dummyArticle.copy(rawDescription = "", fullContent = "")
        )
        coEvery { repo.findArticleById("art_empty") } returns emptyArticle

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_empty", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.requestSummary()
        advanceUntilIdle()

        val state = viewModel.readingUiState.value.summaryState
        assertTrue(state is SummaryState.Error)
        assertEquals(R.string.summary_err_empty_content, (state as SummaryState.Error).messageRes)
    }
}
