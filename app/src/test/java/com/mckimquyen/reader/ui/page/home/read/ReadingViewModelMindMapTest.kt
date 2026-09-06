package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.MindMapNode
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
class ReadingViewModelMindMapTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val summaryService = mockk<GeminiSummaryService>(relaxed = true)
    private val zenAudioManager = mockk<ZenAudioManager>(relaxed = true)

    private val dummyArticle = Article(
        id = "art_map_1",
        date = Date(),
        title = "Concept Mind Mapping in ReadYou",
        author = "Developer",
        rawDescription = "<p>Interactive concept mind maps visualize complex articles with node branches and evidence.</p>",
        shortDescription = "Mind Map Feature",
        fullContent = "<p>Interactive concept mind maps visualize complex articles with node branches and evidence.</p>",
        link = "https://readyou.app",
        feedId = "feed_1",
        accountId = 1,
        isUnread = false,
    )
    private val dummyFeed = Feed(
        id = "feed_1",
        name = "ReadYou Blog",
        url = "https://readyou.app/feed",
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
        every { mockSpanned.toString() } returns "Interactive concept mind maps visualize complex articles with node branches."
        every { androidx.core.text.HtmlCompat.fromHtml(any(), any()) } returns mockSpanned

        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(com.mckimquyen.reader.infrastructure.audio.TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
        coEvery { repo.findArticleById("art_map_1") } returns dummyArticleWithFeed
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(androidx.core.text.HtmlCompat::class)
    }

    @Test
    fun openAndDismissMindMap_updatesSheetVisibilityState() = runTest(testDispatcher) {
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_map_1", autoTtsEnabled = false)
        advanceUntilIdle()

        assertFalse(viewModel.readingUiState.value.showMindMapSheet)

        viewModel.openMindMap()
        assertTrue(viewModel.readingUiState.value.showMindMapSheet)

        viewModel.dismissMindMap()
        assertFalse(viewModel.readingUiState.value.showMindMapSheet)
    }

    @Test
    fun requestMindMap_success_updatesMindMapStateWithGraph() = runTest(testDispatcher) {
        val expectedMindMap = ArticleMindMap(
            rootTitle = "Concept Mind Mapping in ReadYou",
            nodes = listOf(
                MindMapNode(id = "root", label = "Mind Mapping", depth = 0),
                MindMapNode(id = "b1", label = "Canvas Engine", depth = 1, parentId = "root"),
                MindMapNode(id = "s1", label = "Bézier Splines", depth = 2, parentId = "b1")
            ),
            isOfflineFallback = false,
        )
        coEvery { summaryService.generateMindMap(any(), any(), any()) } returns expectedMindMap

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_map_1", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.requestMindMap()
        advanceUntilIdle()

        val state = viewModel.readingUiState.value.mindMapState
        assertTrue(state is MindMapState.Success)
        val success = state as MindMapState.Success
        assertEquals(expectedMindMap, success.mindMap)
        assertEquals(3, success.mindMap.nodes.size)
        assertFalse(success.mindMap.isOfflineFallback)
    }

    @Test
    fun requestMindMap_forceOffline_generatesOfflineMindMap() = runTest(testDispatcher) {
        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_map_1", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.requestMindMap(forceOffline = true)
        advanceUntilIdle()

        val state = viewModel.readingUiState.value.mindMapState
        assertTrue(state is MindMapState.Success)
        val success = state as MindMapState.Success
        assertTrue(success.mindMap.isOfflineFallback)
        assertTrue(success.mindMap.nodes.isNotEmpty())
        assertEquals("Concept Mind Mapping in ReadYou", success.mindMap.rootTitle)
    }

    @Test
    fun requestMindMap_emptyContent_emitsErrorState() = runTest(testDispatcher) {
        val emptySpanned = mockk<android.text.Spanned>()
        every { emptySpanned.toString() } returns ""
        every { androidx.core.text.HtmlCompat.fromHtml(any(), any()) } returns emptySpanned

        val emptyArticle = dummyArticleWithFeed.copy(
            article = dummyArticle.copy(rawDescription = "", fullContent = "")
        )
        coEvery { repo.findArticleById("art_empty_map") } returns emptyArticle

        val viewModel = ReadingViewModel(rssService, rssHelper, ttsManager, summaryService, zenAudioManager)
        viewModel.initData("art_empty_map", autoTtsEnabled = false)
        advanceUntilIdle()

        viewModel.requestMindMap()
        advanceUntilIdle()

        val state = viewModel.readingUiState.value.mindMapState
        assertTrue(state is MindMapState.Error)
        assertEquals(R.string.summary_err_empty_content, (state as MindMapState.Error).messageRes)
    }
}
