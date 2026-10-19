package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelQuoteTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    private fun createViewModel() = ReadingViewModel(
        rssService = rssService,
        rssHelper = rssHelper,
        ttsManager = ttsManager,
        summaryService = summaryService,
        zenAudioManager = zenAudioManager,
        defaultDispatcher = testDispatcher,
    )

    private fun article() = ArticleWithFeed(
        article = Article(
            id = "a1",
            date = Date(),
            title = "Great Title",
            author = "Jane Doe",
            rawDescription = "",
            shortDescription = "",
            link = "https://example.com/a1",
            feedId = "feed-1",
            accountId = 1,
        ),
        feed = Feed(id = "feed-1", name = "BBC News", url = "https://example.com/rss", groupId = "g", accountId = 1),
    )

    @Test
    fun openQuoteSheet_sanitizesQuoteAndBuildsSpecFromLoadedArticle() = runTest(testDispatcher) {
        coEvery { repo.findArticleById("a1") } returns article()
        val vm = createViewModel()
        vm.initData("a1")
        advanceUntilIdle()

        vm.openQuoteSheet("  hello \n  world  ")

        val state = vm.readingUiState.value
        assertTrue(state.showQuoteSheet)
        val spec = state.quoteSpec!!
        assertEquals("hello world", spec.quote)
        assertEquals("BBC News", spec.feedName)
        assertEquals("Jane Doe", spec.author)
        assertEquals("Great Title", spec.articleTitle)
    }

    @Test
    fun openQuoteSheet_ignoresBlankSelection() = runTest(testDispatcher) {
        coEvery { repo.findArticleById("a1") } returns article()
        val vm = createViewModel()
        vm.initData("a1")
        advanceUntilIdle()

        vm.openQuoteSheet("   \n ")

        assertFalse(vm.readingUiState.value.showQuoteSheet)
        assertNull(vm.readingUiState.value.quoteSpec)
    }

    @Test
    fun openQuoteSheet_ignoresWhenNoArticleLoaded() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.openQuoteSheet("some text")

        assertFalse(vm.readingUiState.value.showQuoteSheet)
        assertNull(vm.readingUiState.value.quoteSpec)
    }

    @Test
    fun dismissQuoteSheet_clearsFlagAndSpec() = runTest(testDispatcher) {
        coEvery { repo.findArticleById("a1") } returns article()
        val vm = createViewModel()
        vm.initData("a1")
        advanceUntilIdle()
        vm.openQuoteSheet("hello")

        vm.dismissQuoteSheet()

        assertFalse(vm.readingUiState.value.showQuoteSheet)
        assertNull(vm.readingUiState.value.quoteSpec)
    }
}
