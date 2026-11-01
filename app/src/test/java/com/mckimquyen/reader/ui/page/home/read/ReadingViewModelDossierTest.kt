package com.mckimquyen.reader.ui.page.home.read

import android.content.Intent
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.bounty.BountyInvestigationResult
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.bounty.DossierSharer
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelDossierTest {

    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val articleDao: ArticleDao = mockk()
    private val sharer: DossierSharer = mockk()

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
        every { summaryService.hasCustomApiKey() } returns false
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
        dossierSharer = sharer,
    )

    private fun item(id: String, title: String, feedId: String = "f1") = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id, date = Date(100), title = title, author = null, shortDescription = "", img = null,
            link = "https://e.com/$id", feedId = feedId, accountId = 7, isUnread = true, isStarred = false,
            isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
            audioMime = null, audioDurationSec = null,
        ),
        feed = Feed(id = feedId, name = "Feed $feedId", url = "https://e.com", groupId = "g", accountId = 7),
    )

    private fun loaded() = item("cur", "Current").toArticleWithFeed()

    private suspend fun kotlinx.coroutines.test.TestScope.openResults(
        vm: ReadingViewModel,
        vararg related: ArticleListItemWithFeed,
    ) {
        coEvery { repo.findArticleById("cur") } returns loaded()
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", "DeepSeek", 20) } returns related.toList()
        vm.initData("cur")
        advanceUntilIdle()
        vm.openBountyInvestigation("DeepSeek")
        advanceUntilIdle()
    }

    @Test
    fun openDossier_buildsDossierWhenResultsHaveTwoOrMoreArticles() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"), item("2", "DeepSeek two"))

        vm.openDossier()
        advanceUntilIdle()

        assertNotNull(vm.dossier.value)
        assertEquals("DeepSeek", vm.dossier.value!!.query)
        assertTrue(vm.readingUiState.value.showDossierSheet)
    }

    @Test
    fun openDossier_doesNothingWithFewerThanTwoArticles() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"))

        vm.openDossier()
        advanceUntilIdle()

        assertNull(vm.dossier.value)
        assertFalse(vm.readingUiState.value.showDossierSheet)
    }

    @Test
    fun openDossier_doesNothingBeforeResultsArrive() = runTest(dispatcher) {
        val vm = createViewModel()
        coEvery { repo.findArticleById("cur") } returns loaded()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns
            listOf(item("1", "DeepSeek one"), item("2", "DeepSeek two"))
        vm.initData("cur")
        advanceUntilIdle()
        vm.openBountyInvestigation("DeepSeek")
        runCurrent() // still SCANNING: the minimum radar delay has not elapsed

        vm.openDossier()
        advanceUntilIdle()

        assertNull(vm.dossier.value)
        assertFalse(vm.readingUiState.value.showDossierSheet)
    }

    @Test
    fun openDossier_buildsOnTheDefaultDispatcher() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"), item("2", "DeepSeek two"))

        vm.openDossier()
        assertNull("nothing is built before the dispatcher advances", vm.dossier.value)

        advanceUntilIdle()
        assertNotNull(vm.dossier.value)
    }

    @Test
    fun dismissDossier_clearsDossierAndFlag_butKeepsBountyResults() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"), item("2", "DeepSeek two"))
        vm.openDossier()
        advanceUntilIdle()

        vm.dismissDossier()

        assertNull(vm.dossier.value)
        assertFalse(vm.readingUiState.value.showDossierSheet)
        assertTrue(vm.readingUiState.value.showBountySheet)
        assertNotNull(vm.bountyInvestigationState.value.result)
    }

    @Test
    fun dismissBounty_alsoClearsDossier() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"), item("2", "DeepSeek two"))
        vm.openDossier()
        advanceUntilIdle()

        vm.dismissBountyInvestigation()

        assertNull(vm.dossier.value)
        assertFalse(vm.readingUiState.value.showDossierSheet)
        assertFalse(vm.readingUiState.value.showBountySheet)
    }

    @Test
    fun switchingArticle_clearsDossierAndBountySheets() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"), item("2", "DeepSeek two"))
        vm.openDossier()
        advanceUntilIdle()
        coEvery { repo.findArticleById("other") } returns item("other", "Other").toArticleWithFeed()

        vm.initData("other")
        advanceUntilIdle()

        assertNull(vm.dossier.value)
        assertFalse(vm.readingUiState.value.showDossierSheet)
        assertFalse(vm.readingUiState.value.showBountySheet)
    }

    @Test
    fun prepareDossierShare_returnsIntentFromSharer() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"), item("2", "DeepSeek two"))
        vm.openDossier()
        advanceUntilIdle()
        val file = File("dossier.md")
        val intent = Intent()
        coEvery { sharer.write(any(), "DeepSeek", any()) } returns file
        every { sharer.buildShareIntent(file, "Share dossier") } returns intent

        assertSame(intent, vm.prepareDossierShare("Share dossier"))
    }

    @Test
    fun prepareDossierShare_returnsNullWithoutDossier() = runTest(dispatcher) {
        val vm = createViewModel()

        assertNull(vm.prepareDossierShare("Share dossier"))
    }

    @Test
    fun prepareDossierShare_returnsNullWhenSharerThrows() = runTest(dispatcher) {
        val vm = createViewModel()
        openResults(vm, item("1", "DeepSeek one"), item("2", "DeepSeek two"))
        vm.openDossier()
        advanceUntilIdle()
        coEvery { sharer.write(any(), any(), any()) } throws java.io.IOException("disk full")

        assertNull(vm.prepareDossierShare("Share dossier"))
    }
}
