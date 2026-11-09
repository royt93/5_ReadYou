package com.mckimquyen.reader.ui.page.home.read.mirror

import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.mirror.MirrorToken
import com.mckimquyen.reader.infrastructure.mirror.LanAddressSource
import com.mckimquyen.reader.domain.p2psync.LanGate
import com.mckimquyen.reader.infrastructure.mirror.MirrorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

private const val LONG_LIVED = 10_000_000L
private const val SHORT_LIVED = 10_000L

private class FakeEngine : MirrorEngine {
    var startCalls = 0
    var stopCalls = 0
    var result: MirrorStartResult = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), LONG_LIVED))
    val published = mutableListOf<MirrorSnapshot>()
    val clientCount = MutableStateFlow(0)
    override val clients: StateFlow<Int> get() = clientCount
    override var isRunning = false
    override fun start(bindAddress: InetAddress): MirrorStartResult {
        startCalls++
        if (result is MirrorStartResult.Started) isRunning = true
        return result
    }
    override fun stop() { stopCalls++; isRunning = false }
    override fun publish(snapshot: MirrorSnapshot) { published += snapshot }
}

private class FakeLan(var address: InetAddress? = InetAddress.getByName("192.168.1.20")) : LanAddressSource {
    val losses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override fun current(): InetAddress? = address
    override fun networkLosses(): Flow<Unit> = losses
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class SecondScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val lan = FakeLan()
    private var lanGranted = true

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SecondScreenViewModel(engine, lan, dispatcher, LanGate { lanGranted }).also { it.clock = { 1_000L } }

    @Test fun startsOff() = runTest(dispatcher) {
        assertEquals(MirrorUiState.Off, vm().state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun startOpensTheServerAndShowsTheUrl() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        assertEquals(MirrorUiState.Starting, vm.state.value)
        runCurrent()
        val running = vm.state.value as MirrorUiState.Running
        assertEquals("http://192.168.1.20:8080/m/" + "a".repeat(32), running.url)
        assertEquals(LONG_LIVED, running.expiresAtMillis)
        assertEquals(0, running.clients)
        assertEquals(1, engine.startCalls)
    }

    @Test fun withoutTheLanPermissionNothingIsOpenedAndTheStateSaysWhy() = runTest(dispatcher) {
        lanGranted = false
        val vm = vm()
        vm.start()
        runCurrent()
        assertEquals(MirrorUiState.NoPermission, vm.state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun startAgainAfterTheLanPermissionIsGrantedWorks() = runTest(dispatcher) {
        lanGranted = false
        val vm = vm()
        vm.start()
        lanGranted = true
        vm.start()
        runCurrent()
        assertTrue(vm.state.value is MirrorUiState.Running)
    }

    @Test fun noLanAddressMeansNoNetworkAndTheServerIsNeverOpened() = runTest(dispatcher) {
        lan.address = null
        val vm = vm()
        vm.start(); runCurrent()
        assertEquals(MirrorUiState.NoNetwork, vm.state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun aBindFailureShowsTheErrorKind() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Failed(MirrorFailure.BIND)
        val vm = vm()
        vm.start(); runCurrent()
        assertEquals(MirrorUiState.Error(MirrorFailure.BIND), vm.state.value)
    }

    @Test fun startWhileRunningDoesNotOpenASecondServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.start(); vm.start(); runCurrent()
        assertEquals(1, engine.startCalls)
    }

    @Test fun canRetryAfterAnError() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Failed(MirrorFailure.BIND)
        val vm = vm()
        vm.start(); runCurrent()
        engine.result = MirrorStartResult.Started(9000, MirrorToken("b".repeat(32), LONG_LIVED))
        vm.start(); runCurrent()
        assertTrue(vm.state.value is MirrorUiState.Running)
    }

    @Test fun stopClosesTheServerAndGoesOffIdempotently() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop(); vm.stop()
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertTrue(engine.stopCalls >= 1)
        assertTrue(!engine.isRunning)
    }

    @Test fun goingToTheBackgroundStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.onAppBackgrounded()
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertTrue(!engine.isRunning)
    }

    @Test fun backgroundingWhileOffDoesNothingHarmful() = runTest(dispatcher) {
        val vm = vm()
        vm.onAppBackgrounded()
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun theSessionExpiresWhenTheTokenDoes() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), SHORT_LIVED))
        val vm = vm()
        vm.start(); runCurrent()
        advanceTimeBy(8_999L)
        assertTrue(vm.state.value is MirrorUiState.Running)
        advanceTimeBy(2L)
        assertEquals(MirrorUiState.Expired, vm.state.value)
        assertTrue(!engine.isRunning)
    }

    @Test fun aStoppedSessionNeverTurnsIntoExpiredLater() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), SHORT_LIVED))
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop()
        val stopsAfterStop = engine.stopCalls
        advanceTimeBy(SHORT_LIVED * 3)
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertEquals("a cancelled expiry timer must not stop the engine again", stopsAfterStop, engine.stopCalls)
    }

    @Test fun aRestartedSessionIsNotCutShortByTheOldTimer() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), SHORT_LIVED))
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop()
        engine.result = MirrorStartResult.Started(8081, MirrorToken("b".repeat(32), LONG_LIVED))
        vm.start(); runCurrent()
        advanceTimeBy(SHORT_LIVED * 3)
        assertTrue(vm.state.value is MirrorUiState.Running)
        assertTrue(engine.isRunning)
    }

    @Test fun losingTheNetworkStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        lan.losses.tryEmit(Unit)
        runCurrent()
        assertEquals(MirrorUiState.NoNetwork, vm.state.value)
        assertTrue(!engine.isRunning)
    }

    @Test fun theViewerCountFollowsTheEngine() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        engine.clientCount.value = 2
        runCurrent()
        assertEquals(2, (vm.state.value as MirrorUiState.Running).clients)
    }

    @Test fun anArticleSentBeforeStartIsPublishedWhenTheServerStarts() = runTest(dispatcher) {
        val vm = vm()
        vm.updateArticle("Title", "BBC", 5L, "<p>Hello</p><p>World</p>")
        runCurrent()
        assertTrue(engine.published.isEmpty())
        vm.start(); runCurrent()
        val s = engine.published.last()
        assertEquals("Title", s.title)
        assertEquals("BBC", s.source)
        assertEquals(listOf("Hello", "World"), s.paragraphs)
    }

    @Test fun eachNewArticleIsPublishedWithAHigherVersionWhileRunning() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.updateArticle("One", "S", 1L, "<p>a</p>"); runCurrent()
        vm.updateArticle("Two", "S", 2L, "<p>b</p>"); runCurrent()
        assertEquals(listOf("One", "Two"), engine.published.map { it.title }.takeLast(2))
        assertTrue(engine.published.last().version > engine.published[engine.published.size - 2].version)
    }

    @Test fun nothingIsPublishedAfterStop() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop()
        val before = engine.published.size
        vm.updateArticle("Late", "S", 1L, "<p>x</p>"); runCurrent()
        assertEquals(before, engine.published.size)
    }

    @Test fun nullOrBlankHtmlGivesAnEmptyBodyNotACrash() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.updateArticle("T", "S", 1L, null); runCurrent()
        assertTrue(engine.published.last().paragraphs.isEmpty())
        vm.updateArticle("T2", "S", 1L, "   "); runCurrent()
        assertTrue(engine.published.last().paragraphs.isEmpty())
    }

    @Test fun scriptsInTheArticleNeverReachTheSnapshotAsMarkup() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.updateArticle("T", "S", 1L, "<p>safe</p><script>alert(1)</script><img src=x onerror=y>"); runCurrent()
        val text = engine.published.last().paragraphs.joinToString(" ")
        assertTrue(!text.contains("<script") && !text.contains("onerror") && !text.contains("<img"))
    }

    @Test fun clearingTheViewModelStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.callOnCleared()
        assertTrue(!engine.isRunning)
    }
}
