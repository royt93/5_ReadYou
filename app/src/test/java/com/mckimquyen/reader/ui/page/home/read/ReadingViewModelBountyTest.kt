package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.bounty.BountyInvestigationResult
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.BountyInvestigationRepository
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.ui.page.home.read.bounty.BountyAiState
import com.mckimquyen.reader.ui.page.home.read.bounty.BountyInvestigationPhase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class ReadingViewModelBountyTest {

    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val articleDao: ArticleDao = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
        defaultDispatcher = dispatcher,
        articleDao = articleDao,
    )

    private fun currentArticle() = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = "cur", date = Date(100), title = "Current", author = null, shortDescription = "", img = null,
            link = "https://e.com/cur", feedId = "f1", accountId = 7, isUnread = true, isStarred = false,
            isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
            audioMime = null, audioDurationSec = null,
        ),
        feed = Feed(id = "f1", name = "Feed", url = "https://e.com", groupId = "g", accountId = 7),
    )

    private fun loaded() = currentArticle().toArticleWithFeed()

    private fun result(query: String) = BountyInvestigationResult(query, emptyList(), 0, null, null)

    @Test
    fun openInvestigation_scansThenPublishesLocalResultsWithCurrentAccountScope() = runTest(dispatcher) {
        coEvery { repo.findArticleById("cur") } returns loaded()
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", "DeepSeek", 20) } returns emptyList()
        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        vm.openBountyInvestigation("  DeepSeek. Extra sentence")
        runCurrent()
        assertEquals(BountyInvestigationPhase.SCANNING, vm.bountyInvestigationState.value.phase)
        assertEquals("DeepSeek", vm.bountyInvestigationState.value.query)

        advanceTimeBy(700)
        runCurrent()
        assertEquals(BountyInvestigationPhase.RESULTS, vm.bountyInvestigationState.value.phase)
        assertEquals("DeepSeek", vm.bountyInvestigationState.value.result?.query)
        coVerify(exactly = 1) { articleDao.queryRelatedArticleListWithFeed(7, "cur", "DeepSeek", 20) }
    }

    @Test
    fun invalidQuery_doesNotOpenOrQuery() = runTest(dispatcher) {
        coEvery { repo.findArticleById("cur") } returns loaded()
        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        vm.openBountyInvestigation(" ")

        assertFalse(vm.readingUiState.value.showBountySheet)
        coVerify(exactly = 0) { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) }
    }

    @Test
    fun dismissInvestigation_cancelsLookupAndResetsState() = runTest(dispatcher) {
        coEvery { repo.findArticleById("cur") } returns loaded()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }
        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()
        vm.openBountyInvestigation("DeepSeek")
        runCurrent()

        vm.dismissBountyInvestigation()
        advanceUntilIdle()

        assertFalse(vm.readingUiState.value.showBountySheet)
        assertEquals(BountyInvestigationPhase.SCANNING, vm.bountyInvestigationState.value.phase)
        assertNull(vm.bountyInvestigationState.value.result)
    }

    @Test
    fun lookupFailure_setsErrorStateWithoutCrashingReader() = runTest(dispatcher) {
        coEvery { repo.findArticleById("cur") } returns loaded()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } throws IllegalStateException("db")
        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()
        vm.openBountyInvestigation("DeepSeek")
        advanceUntilIdle()

        assertEquals(BountyInvestigationPhase.ERROR, vm.bountyInvestigationState.value.phase)
        assertTrue(vm.readingUiState.value.showBountySheet)
    }

    @Test
    fun noCustomKey_hidesAiActionAndNeverCallsAiService() = runTest(dispatcher) {
        coEvery { repo.findArticleById("cur") } returns loaded()
        every { summaryService.hasCustomApiKey() } returns false
        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()
        vm.openBountyInvestigation("DeepSeek")
        advanceUntilIdle()

        assertFalse(vm.bountyInvestigationState.value.canUseAiContext)
        vm.requestBountyAiContext()
        advanceUntilIdle()
        coVerify(exactly = 0) { summaryService.summarizeBountyContext(any(), any()) }
    }

    @Test
    fun aiSummaryUsesSameBoundedLocalResults() = runTest(dispatcher) {
        val localResult = result("DeepSeek")
        coEvery { repo.findArticleById("cur") } returns loaded()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns emptyList()
        every { summaryService.hasCustomApiKey() } returns true
        coEvery { summaryService.summarizeBountyContext("DeepSeek", localResult.articles) } returns "context"
        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()
        vm.openBountyInvestigation("DeepSeek")
        advanceUntilIdle()
        vm.requestBountyAiContext()
        advanceUntilIdle()

        assertEquals(BountyAiState.Ready("context"), vm.bountyInvestigationState.value.ai)
        coVerify(exactly = 1) { summaryService.summarizeBountyContext("DeepSeek", emptyList()) }
    }
}
