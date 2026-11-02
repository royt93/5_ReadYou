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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelSecondScreenTest {
    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val feed = Feed("f1", "BBC", url = "https://x.test", groupId = "g", accountId = 1)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
    }

    @After fun tearDown() { Dispatchers.resetMain(); io.mockk.unmockkAll() }

    private fun vm() = ReadingViewModel(
        rssService = rssService, rssHelper = mockk(relaxed = true), ttsManager = ttsManager,
        summaryService = mockk<GeminiSummaryService>(relaxed = true), zenAudioManager = zenAudioManager,
        defaultDispatcher = dispatcher, ioDispatcher = dispatcher,
    )

    private fun article() = ArticleWithFeed(
        Article(id = "a1", date = Date(1L), title = "Title", rawDescription = "<p>x</p>", shortDescription = "x",
            link = "https://x.test/a1", feedId = "f1", accountId = 1), feed)

    @Test fun openWithoutAnArticleIsANoOp() = runTest(dispatcher) {
        val vm = vm()
        vm.openSecondScreen()
        assertFalse(vm.readingUiState.value.showSecondScreenSheet)
    }

    @Test fun openAndDismissToggleTheFlagWhenAnArticleIsLoaded() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article()
        val vm = vm()
        vm.initData("a1"); advanceUntilIdle()
        vm.openSecondScreen()
        assertTrue(vm.readingUiState.value.showSecondScreenSheet)
        vm.dismissSecondScreen()
        assertFalse(vm.readingUiState.value.showSecondScreenSheet)
    }

    @Test fun openingItDoesNotOpenAnyOtherSheet() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article()
        val vm = vm()
        vm.initData("a1"); advanceUntilIdle()
        vm.openSecondScreen()
        val s = vm.readingUiState.value
        assertFalse(s.showSummarySheet || s.showMindMapSheet || s.showDeepReadSheet || s.showQuoteSheet ||
            s.showBountySheet || s.showEchoSheet || s.showDossierSheet || s.showDualCardSheet)
    }
}
