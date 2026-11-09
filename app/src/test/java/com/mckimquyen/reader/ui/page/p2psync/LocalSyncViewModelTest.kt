package com.mckimquyen.reader.ui.page.p2psync

import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.mirror.MirrorToken
import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlan
import com.mckimquyen.reader.domain.p2psync.PairingSecret
import com.mckimquyen.reader.domain.p2psync.SyncArticleState
import com.mckimquyen.reader.domain.p2psync.SyncEnvelopeCrypto
import com.mckimquyen.reader.domain.p2psync.SyncFeed
import com.mckimquyen.reader.domain.p2psync.SyncPackage
import com.mckimquyen.reader.domain.p2psync.SyncPackageCodec
import com.mckimquyen.reader.domain.p2psync.SyncPackageResult
import com.mckimquyen.reader.domain.p2psync.SyncSessionTransport
import com.mckimquyen.reader.infrastructure.mirror.LanAddressSource
import com.mckimquyen.reader.infrastructure.mirror.SyncSessionEngine
import com.mckimquyen.reader.infrastructure.p2psync.LocalSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

private const val LONG_LIVED = 10_000_000L
private const val SHORT_LIVED = 10_000L
private const val LOCAL_ACCOUNT = 1

private class FakeSyncEngine : SyncSessionEngine {
    var startCalls = 0
    var stopCalls = 0
    var lastCiphertext: ByteArray? = null
    var result: MirrorStartResult = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), LONG_LIVED))
    override var isRunning = false
    override fun startSync(bindAddress: InetAddress, ciphertext: ByteArray): MirrorStartResult {
        startCalls++
        lastCiphertext = ciphertext.copyOf()
        if (result is MirrorStartResult.Started) isRunning = true
        return result
    }

    var startWithTokenCalls = 0
    var lastToken: String? = null
    override fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String): MirrorStartResult {
        startWithTokenCalls++
        lastToken = tokenValue
        lastCiphertext = ciphertext.copyOf()
        if (result is MirrorStartResult.Started) isRunning = true
        return result
    }

    override fun stop() {
        stopCalls++
        isRunning = false
    }
}

private class FakeLan(var address: InetAddress? = InetAddress.getByName("192.168.1.20")) : LanAddressSource {
    val losses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override fun current(): InetAddress? = address
    override fun networkLosses(): Flow<Unit> = losses
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeSyncEngine()
    private val lan = FakeLan()
    private val repository = mockk<LocalSyncRepository>()
    private val transport = mockk<SyncSessionTransport>()

    private val outgoing = SyncPackage(
        1, "a".repeat(32), 5L,
        listOf(SyncFeed("https://a.test/rss", "News", "World")),
        listOf(SyncArticleState("https://a.test/rss", "https://a.test/story", false, true, 10L)),
    )
    private val plan = LocalSyncPlan(emptyList(), 1, 1, 0, emptyList())
    private val applied = LocalSyncApplyResult(0, 1, 1, 0, 1)
    private val sessionUrl = "http://192.168.1.30:4321/s/${"c".repeat(32)}"
    private val secret = PairingSecret.fromHex("0123456789abcdef".repeat(2))

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.snapshot(LOCAL_ACCOUNT) } returns outgoing
        coEvery { repository.preview(LOCAL_ACCOUNT, any()) } returns plan
        coEvery { repository.apply(LOCAL_ACCOUNT, any()) } returns applied
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = LocalSyncViewModel(repository, transport, engine, lan, dispatcher, dispatcher, mockk(relaxed = true), mockk(relaxed = true)) { true }.also {
        it.clock = { 1_000L }
    }

    private fun envelope(pkg: SyncPackage = outgoing, with: PairingSecret = secret): ByteArray =
        SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(pkg), with)

    private fun error(vm: LocalSyncViewModel) = (vm.state.value as LocalSyncUiState.Error).kind

    // ---- Send ----

    @Test fun startsIdleWithNothingOpened() = runTest(dispatcher) {
        assertEquals(LocalSyncUiState.Idle, vm().state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun sendShowsSecretOnlyAfterTheServerStartedAndKeepsItOutOfTheUrl() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT)
        assertEquals(LocalSyncUiState.Starting, vm.state.value)
        assertEquals(0, engine.startCalls)
        runCurrent()
        val sending = vm.state.value as LocalSyncUiState.Sending
        val secretHex = requireNotNull(sending.secretHex) { "manual send always shows its secret" }
        assertEquals("http://192.168.1.20:8080/s/" + "a".repeat(32), sending.url)
        assertEquals(32, secretHex.length)
        assertFalse(sending.url.contains(secretHex))
        assertEquals(LONG_LIVED, sending.expiresAtMillis)
    }

    @Test fun theServedEnvelopeIsCiphertextThatOnlyTheShownSecretOpens() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        val sending = vm.state.value as LocalSyncUiState.Sending
        val secretHex = requireNotNull(sending.secretHex) { "manual send always shows its secret" }
        val served = engine.lastCiphertext!!
        assertFalse(String(served, Charsets.ISO_8859_1).contains("a.test"))
        val plain = SyncEnvelopeCrypto.decrypt(served, PairingSecret.fromHex(secretHex))
        assertEquals(outgoing, (SyncPackageCodec.decodeAndValidate(plain) as SyncPackageResult.Valid).data)
        assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(served, secret) }.isFailure)
    }

    @Test fun sendingStateNeverPrintsTheSecret() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        val sending = vm.state.value as LocalSyncUiState.Sending
        assertFalse(sending.toString().contains(requireNotNull(sending.secretHex)))
    }

    @Test fun noLanAddressMeansNoNetworkAndNothingIsOpened() = runTest(dispatcher) {
        lan.address = null
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.NO_NETWORK, error(vm))
        assertEquals(0, engine.startCalls)
    }

    @Test fun nonLocalAccountNeverStartsAServer() = runTest(dispatcher) {
        coEvery { repository.snapshot(LOCAL_ACCOUNT) } throws IllegalArgumentException("not local")
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.NOT_LOCAL, error(vm))
        assertEquals(0, engine.startCalls)
    }

    @Test fun bindFailureShowsAnErrorWithoutAnySecret() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Failed(MirrorFailure.BIND)
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.SERVER, error(vm))
        assertFalse(engine.isRunning)
    }

    @Test fun startWhileSendingDoesNotOpenASecondServer() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        vm.startSend(LOCAL_ACCOUNT); vm.startSend(LOCAL_ACCOUNT); runCurrent()
        assertEquals(1, engine.startCalls)
    }

    @Test fun stopSendClosesTheServerAndForgetsTheSecret() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        vm.stopSend(); vm.stopSend()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertFalse(engine.isRunning)
    }

    @Test fun leavingTheScreenStopsTheServerAndClearsEverything() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        vm.onScreenStopped()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertFalse(engine.isRunning)
    }

    @Test fun losingTheNetworkStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        lan.losses.tryEmit(Unit); runCurrent()
        assertEquals(LocalSyncError.NO_NETWORK, error(vm))
        assertFalse(engine.isRunning)
    }

    @Test fun theSessionExpiresWhenTheTokenDoes() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), SHORT_LIVED))
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        advanceTimeBy(8_999L)
        assertTrue(vm.state.value is LocalSyncUiState.Sending)
        advanceTimeBy(2L)
        assertEquals(LocalSyncError.EXPIRED, error(vm))
        assertFalse(engine.isRunning)
    }

    @Test fun aStoppedSessionNeverBecomesExpiredLater() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), SHORT_LIVED))
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        vm.stopSend()
        val stops = engine.stopCalls
        advanceTimeBy(SHORT_LIVED * 3)
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertEquals(stops, engine.stopCalls)
    }

    @Test fun clearingTheViewModelStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        vm.callOnCleared()
        assertFalse(engine.isRunning)
    }

    // ---- Receive ----

    @Test fun receiveDecryptsAndValidatesBeforeShowingAPreviewAndNeverWrites() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex)
        assertEquals(LocalSyncUiState.Receiving, vm.state.value)
        runCurrent()
        val preview = vm.state.value as LocalSyncUiState.Preview
        assertEquals(outgoing, preview.incoming)
        assertEquals(plan, preview.plan)
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun malformedSecretIsRejectedBeforeAnyNetworkAccess() = runTest(dispatcher) {
        val vm = vm()
        listOf("", "short", "G".repeat(32), "A".repeat(32)).forEach {
            vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, it); runCurrent()
            assertEquals(LocalSyncError.SECRET, error(vm))
        }
        verify(exactly = 0) { transport.fetch(any()) }
    }

    @Test fun invalidOrPublicUrlIsRejectedBeforeAnyNetworkAccess() = runTest(dispatcher) {
        val vm = vm()
        listOf("", "https://192.168.1.30:1/s/${"c".repeat(32)}", "http://example.com:1/s/${"c".repeat(32)}").forEach {
            vm.loadPreview(LOCAL_ACCOUNT, it, secret.hex); runCurrent()
            assertEquals(LocalSyncError.URL, error(vm))
        }
        verify(exactly = 0) { transport.fetch(any()) }
    }

    @Test fun networkFailureShowsAnErrorAndWritesNothing() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } throws java.io.IOException("down")
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        assertEquals(LocalSyncError.NETWORK, error(vm))
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun wrongSecretFailsAuthenticationAndNeverReachesThePlannerOrDatabase() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope(with = PairingSecret.fromHex("f".repeat(32)))
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        assertEquals(LocalSyncError.PACKAGE, error(vm))
        coVerify(exactly = 0) { repository.preview(any(), any()) }
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun tamperedEnvelopeIsRejected() = runTest(dispatcher) {
        val bad = envelope().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 1).toByte() }
        every { transport.fetch(sessionUrl) } returns bad
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        assertEquals(LocalSyncError.PACKAGE, error(vm))
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun authenticCiphertextWithAnInvalidPackageIsRejected() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns SyncEnvelopeCrypto.encrypt("""{"version":9}""".toByteArray(), secret)
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        assertEquals(LocalSyncError.PACKAGE, error(vm))
        coVerify(exactly = 0) { repository.preview(any(), any()) }
    }

    @Test fun nonLocalReceiverIsRejectedAtPreview() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        coEvery { repository.preview(LOCAL_ACCOUNT, any()) } throws IllegalArgumentException("not local")
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        assertEquals(LocalSyncError.NOT_LOCAL, error(vm))
    }

    @Test fun cancelDropsThePreviewAndNeverApplies() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        vm.cancelPreview()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        vm.applyPreview(LOCAL_ACCOUNT); runCurrent()
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun applyWritesOnlyAfterAPreviewAndOnlyOnce() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val vm = vm()
        vm.applyPreview(LOCAL_ACCOUNT); runCurrent()
        coVerify(exactly = 0) { repository.apply(any(), any()) }
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        vm.applyPreview(LOCAL_ACCOUNT)
        assertEquals(LocalSyncUiState.Applying, vm.state.value)
        vm.applyPreview(LOCAL_ACCOUNT); vm.applyPreview(LOCAL_ACCOUNT); runCurrent()
        assertEquals(LocalSyncUiState.Done(applied), vm.state.value)
        vm.applyPreview(LOCAL_ACCOUNT); runCurrent()
        coVerify(exactly = 1) { repository.apply(LOCAL_ACCOUNT, outgoing) }
    }

    @Test fun previewCannotBeAppliedToAnotherLocalAccount() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        vm.applyPreview(LOCAL_ACCOUNT + 1); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun multipleApplyTapsWhileDatabaseIsWorkingStartOnlyOneWrite() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val gate = CompletableDeferred<LocalSyncApplyResult>()
        coEvery { repository.apply(LOCAL_ACCOUNT, any()) } coAnswers { gate.await() }
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        vm.applyPreview(LOCAL_ACCOUNT)
        vm.applyPreview(LOCAL_ACCOUNT)
        vm.applyPreview(LOCAL_ACCOUNT)
        runCurrent()
        assertEquals(LocalSyncUiState.Applying, vm.state.value)
        coVerify(exactly = 1) { repository.apply(LOCAL_ACCOUNT, outgoing) }
        gate.complete(applied)
        runCurrent()
        assertEquals(LocalSyncUiState.Done(applied), vm.state.value)
    }

    @Test fun databaseFailureShowsAnErrorAndKeepsNoPlaintextPackage() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        coEvery { repository.apply(LOCAL_ACCOUNT, any()) } throws IllegalStateException("db")
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        vm.applyPreview(LOCAL_ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.DATABASE, error(vm))
        assertNotNull(vm.state.value)
        assertFalse(vm.state.value.toString().contains("a.test"))
    }

    @Test fun leavingTheScreenDropsAPendingPreview() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        vm.onScreenStopped()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        vm.applyPreview(LOCAL_ACCOUNT); runCurrent()
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun previewStateNeverPrintsFeedUrls() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        assertFalse(vm.state.value.toString().contains("a.test"))
    }

    @Test fun anApplyThatIsAlreadyRunningSurvivesLeavingTheScreen() = runTest(dispatcher) {
        every { transport.fetch(sessionUrl) } returns envelope()
        val vm = vm()
        vm.loadPreview(LOCAL_ACCOUNT, sessionUrl, secret.hex); runCurrent()
        vm.applyPreview(LOCAL_ACCOUNT)
        vm.onScreenStopped(); runCurrent()
        coVerify(exactly = 1) { repository.apply(LOCAL_ACCOUNT, outgoing) }
    }

    @Test fun envelopeBytesGivenToTheEngineAreACopyOfWhatWasEncrypted() = runTest(dispatcher) {
        val vm = vm()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        val first = engine.lastCiphertext!!.copyOf()
        vm.stopSend()
        vm.startSend(LOCAL_ACCOUNT); runCurrent()
        assertFalse("fresh salt and nonce on every session", first.contentEquals(engine.lastCiphertext))
        assertArrayEquals(first.copyOfRange(0, 5), engine.lastCiphertext!!.copyOfRange(0, 5))
    }
}
