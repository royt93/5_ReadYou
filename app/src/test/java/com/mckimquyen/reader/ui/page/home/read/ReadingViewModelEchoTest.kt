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
import com.mckimquyen.reader.ui.page.headline.EchoSignalState
import com.mckimquyen.reader.ui.page.headline.readyResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReadingViewModelEchoTest {

    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)

    private val promotional = """
        <p>Introducing the revolutionary, game-changing platform that is the best ever solution on the market!!!</p>
        <p>This world-class, groundbreaking product is guaranteed to deliver unparalleled results, seamlessly and exclusively for you.</p>
        <p>Our unbeatable breakthrough is a must-have for every company that wants to grow its industry.</p>
    """.trimIndent()

    private val factual = """
        <p>The council approved a new tram route on Tuesday after a vote that ended seven to four.</p>
        <p>Construction is expected to begin next spring and will take about two years, according to the transport department.</p>
    """.trimIndent()

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
    )

    private fun article(id: String, body: String) = ArticleWithFeed(
        article = Article(
            id = id, date = Date(), title = "Title $id", rawDescription = body, shortDescription = "",
            link = "https://example.com/$id", feedId = "feed-1", accountId = 1,
        ),
        feed = Feed(id = "feed-1", name = "BBC", url = "https://example.com/rss", groupId = "g", accountId = 1),
    )

    @Test
    fun promotionalBody_becomesReadyWithNoteworthyResult() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article("a1", promotional)
        val vm = createViewModel()

        vm.initData("a1")
        advanceUntilIdle()

        val state = vm.echoSignalState.value
        assertTrue("state=$state", state is EchoSignalState.Ready)
        assertTrue(state.readyResult()!!.isNoteworthy)
        assertTrue(state.readyResult()!!.hasHypeWarning)
    }

    @Test
    fun factualBody_endsInNone_notReady() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article("a1", factual)
        val vm = createViewModel()

        vm.initData("a1")
        advanceUntilIdle()

        assertEquals(EchoSignalState.None, vm.echoSignalState.value)
    }

    @Test
    fun blankBody_staysIdleOrNone_neverReady() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article("a1", "   ")
        val vm = createViewModel()

        vm.initData("a1")
        advanceUntilIdle()

        assertFalse(vm.echoSignalState.value is EchoSignalState.Ready)
        assertFalse(vm.echoSignalState.value is EchoSignalState.Analyzing)
    }

    @Test
    fun switchingArticle_dropsTheOldResultSoItCannotLeakToTheNewArticle() = runTest(dispatcher) {
        coEvery { repo.findArticleById("promo") } returns article("promo", promotional)
        coEvery { repo.findArticleById("plain") } returns article("plain", factual)
        val vm = createViewModel()

        vm.initData("promo")
        advanceUntilIdle()
        assertTrue(vm.echoSignalState.value is EchoSignalState.Ready)

        vm.initData("plain")
        runCurrent()
        assertFalse("old result must not survive an article switch", vm.echoSignalState.value is EchoSignalState.Ready)
        advanceUntilIdle()
        assertEquals(EchoSignalState.None, vm.echoSignalState.value)
    }

    @Test
    fun contentChange_reanalyzes_forFullContentRender() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article("a1", factual)
        coEvery { rssHelper.parseFullContent(any(), any(), any()) } returns promotional
        val vm = createViewModel()
        vm.initData("a1")
        advanceUntilIdle()
        assertEquals(EchoSignalState.None, vm.echoSignalState.value)

        vm.renderFullContent()
        advanceUntilIdle()

        assertTrue("state=${vm.echoSignalState.value}", vm.echoSignalState.value is EchoSignalState.Ready)
    }

    @Test
    fun echoSheet_opensOnlyWithAReadyResult_andDismissResetsIt() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article("a1", promotional)
        val vm = createViewModel()
        vm.initData("a1")
        advanceUntilIdle()

        vm.openEchoSheet()
        assertTrue(vm.readingUiState.value.showEchoSheet)

        vm.dismissEchoSheet()
        assertFalse(vm.readingUiState.value.showEchoSheet)
    }

    @Test
    fun echoSheet_refusesToOpenWithoutAReadyResult() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article("a1", factual)
        val vm = createViewModel()
        vm.initData("a1")
        advanceUntilIdle()

        vm.openEchoSheet()

        assertFalse(vm.readingUiState.value.showEchoSheet)
        assertNotNull(vm.echoSignalState.value)
    }
}
