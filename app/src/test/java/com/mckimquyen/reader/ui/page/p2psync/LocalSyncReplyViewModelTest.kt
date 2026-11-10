package com.mckimquyen.reader.ui.page.p2psync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.mirror.MirrorToken
import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlan
import com.mckimquyen.reader.domain.p2psync.PairingSecret
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import com.mckimquyen.reader.domain.p2psync.SessionTokenDerivation
import com.mckimquyen.reader.domain.p2psync.SyncArticleState
import com.mckimquyen.reader.domain.p2psync.SyncEnvelopeCrypto
import com.mckimquyen.reader.domain.p2psync.SyncFeed
import com.mckimquyen.reader.domain.p2psync.SyncPackage
import com.mckimquyen.reader.domain.p2psync.SyncPackageCodec
import com.mckimquyen.reader.domain.p2psync.SyncSessionTransport
import com.mckimquyen.reader.infrastructure.mirror.LanAddressSource
import com.mckimquyen.reader.infrastructure.mirror.SyncSessionEngine
import com.mckimquyen.reader.infrastructure.p2psync.LocalSyncRepository
import com.mckimquyen.reader.infrastructure.p2psync.NsdPlatform
import com.mckimquyen.reader.infrastructure.p2psync.PairingStore
import com.mckimquyen.reader.infrastructure.p2psync.PeerDiscovery
import com.mckimquyen.reader.infrastructure.p2psync.SecretWrapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

private const val REPLY_ACCOUNT = 1
private const val REPLY_TTL = 10_000_000L
private const val PEER_HOST = "192.168.1.30"
private const val PEER_PORT = 4321

private class ReplyFakeEngine : SyncSessionEngine {
    var tokenCalls = 0
    var lastCiphertext: ByteArray? = null
    var stops = 0
    override var isRunning = false
    override fun startSync(bindAddress: InetAddress, ciphertext: ByteArray): MirrorStartResult {
        isRunning = true
        return MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), REPLY_TTL))
    }
    override fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String): MirrorStartResult {
        tokenCalls++
        lastCiphertext = ciphertext.copyOf()
        isRunning = true
        return MirrorStartResult.Started(8080, MirrorToken(tokenValue, REPLY_TTL))
    }
    override fun stop() { stops++; isRunning = false }
}

private class ReplyFakeLan : LanAddressSource {
    val losses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override fun current(): InetAddress? = InetAddress.getByName("192.168.1.20")
    override fun networkLosses(): Flow<Unit> = losses
}

private class ReplyFakeNsd : NsdPlatform {
    var unregisters = 0
    var discoveries = 0
    override fun register(serviceName: String, port: Int, attributes: Map<String, ByteArray>, onResult: (Boolean) -> Unit) = onResult(true)
    override fun unregister() { unregisters++ }
    override fun discover(onFound: (String, String?, Int, Map<String, ByteArray?>) -> Unit, onLost: (String) -> Unit, onFailed: () -> Unit) { discoveries++ }
    override fun stopDiscovery() = Unit
}

private class ReplyFakeWrapper : SecretWrapper {
    var keyPresent = true
    override fun wrap(plain: ByteArray) = byteArrayOf(0x7F) + ByteArray(plain.size) { (plain[it].toInt() xor 0x5A).toByte() }
    override fun unwrap(wrapped: ByteArray): ByteArray? =
        if (!keyPresent || wrapped.isEmpty()) null else ByteArray(wrapped.size - 1) { (wrapped[it + 1].toInt() xor 0x5A).toByte() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSyncReplyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = ReplyFakeEngine()
    private val lan = ReplyFakeLan()
    private val nsd = ReplyFakeNsd()
    private val wrapper = ReplyFakeWrapper()
    private val repository = mockk<LocalSyncRepository>()
    private val transport = mockk<SyncSessionTransport>()
    private lateinit var store: PairingStore
    private lateinit var discovery: PeerDiscovery
    private var lanGranted = true
    private val peerSecret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")

    private val outgoing = SyncPackage(
        1, "a".repeat(32), 5L,
        listOf(SyncFeed("https://a.test/rss", "News", "World")),
        listOf(SyncArticleState("https://a.test/rss", "https://a.test/story", false, true, 10L)),
    )
    private val plan = LocalSyncPlan(emptyList(), 1, 1, 0, emptyList())

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PairingStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = PairingStore(context, wrapper)
        discovery = PeerDiscovery(nsd)
        coEvery { repository.snapshot(REPLY_ACCOUNT) } returns outgoing
        coEvery { repository.preview(REPLY_ACCOUNT, any()) } returns plan
        coEvery { repository.apply(REPLY_ACCOUNT, any()) } returns LocalSyncApplyResult(0, 1, 1, 0, 1)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(PairingStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun vm() = LocalSyncViewModel(repository, transport, engine, lan, dispatcher, dispatcher, store, discovery) { lanGranted }
        .also { it.clock = { 1_000L } }

    private fun error(vm: LocalSyncViewModel) = (vm.state.value as LocalSyncUiState.Error).kind

    /** Stubs the transport for the URL a paired receive of [nonce] will build and returns that nonce. */
    private fun receivableFrom(nonce: ByteArray = SessionTokenDerivation.newNonce()): ByteArray {
        val url = "http://$PEER_HOST:$PEER_PORT/s/${SessionTokenDerivation.derive(peerSecret, nonce)}"
        every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), peerSecret)
        return nonce
    }

    /** A verified paired receive that has reached the preview. */
    private fun LocalSyncViewModel.receiveFromPeerNow(peerId: String) {
        receiveFromPaired(REPLY_ACCOUNT, peerId, PEER_HOST, PEER_PORT, receivableFrom())
    }

    @Test fun aVerifiedPairedReceiveRemembersThePeerAndAccount() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
    }

    @Test fun aFailedDecryptNeverCreatesAReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val nonce = SessionTokenDerivation.newNonce()
        val url = "http://$PEER_HOST:$PEER_PORT/s/${SessionTokenDerivation.derive(peerSecret, nonce)}"
        every { transport.fetch(url) } returns
            SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), PairingSecret.fromHex("f".repeat(32)))
        val vm = vm()
        vm.receiveFromPaired(REPLY_ACCOUNT, peer.peerId, PEER_HOST, PEER_PORT, nonce); runCurrent()
        assertEquals(LocalSyncError.PACKAGE, error(vm))
        assertNull(vm.replyPeer.value)
    }

    @Test fun aFailedFetchClearsAnEarlierReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
        val nonce = SessionTokenDerivation.newNonce()
        val url = "http://$PEER_HOST:$PEER_PORT/s/${SessionTokenDerivation.derive(peerSecret, nonce)}"
        every { transport.fetch(url) } throws java.io.IOException("down")
        vm.receiveFromPaired(REPLY_ACCOUNT, peer.peerId, PEER_HOST, PEER_PORT, nonce); runCurrent()
        assertEquals(LocalSyncError.NETWORK, error(vm))
        assertNull(vm.replyPeer.value)
    }

    @Test fun aLabelledManualReceiveRemembersTheJustStoredPeerSoItCanBeAnsweredAtOnce() = runTest(dispatcher) {
        val url = "http://$PEER_HOST:$PEER_PORT/s/${"c".repeat(32)}"
        every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), peerSecret)
        val vm = vm()
        vm.loadPreview(REPLY_ACCOUNT, url, peerSecret.hex, "Tablet"); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        val stored = store.list().single()
        assertEquals(ReplyPeer(REPLY_ACCOUNT, stored.peerId), vm.replyPeer.value)
        vm.cancelPreview()
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        val sending = vm.state.value as LocalSyncUiState.Sending
        assertEquals(stored.peerId, sending.pairedPeerId)
        assertNull(sending.secretHex)
    }

    @Test fun aLabelledManualReceiveThatFailsToAuthenticateRemembersNothing() = runTest(dispatcher) {
        val url = "http://$PEER_HOST:$PEER_PORT/s/${"c".repeat(32)}"
        every { transport.fetch(url) } returns
            SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), PairingSecret.fromHex("f".repeat(32)))
        val vm = vm()
        vm.loadPreview(REPLY_ACCOUNT, url, peerSecret.hex, "Tablet"); runCurrent()
        assertEquals(LocalSyncError.PACKAGE, error(vm))
        assertNull(vm.replyPeer.value)
    }

    @Test fun aManualReceiveNeverCreatesAReplyPeer() = runTest(dispatcher) {
        val url = "http://$PEER_HOST:$PEER_PORT/s/${"c".repeat(32)}"
        every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), peerSecret)
        val vm = vm()
        vm.loadPreview(REPLY_ACCOUNT, url, peerSecret.hex); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        assertNull(vm.replyPeer.value)
    }

    @Test fun userCancelKeepsTheReplyPeerAndAppliesNothing() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    @Test fun leavingTheScreenClearsTheReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.onScreenStopped()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertNull(vm.replyPeer.value)
    }

    @Test fun aSuccessfulApplyKeepsTheReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.applyPreview(REPLY_ACCOUNT); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Done)
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
    }

    @Test fun aFailedApplyClearsTheReplyPeerAndShowsTheDatabaseError() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        coEvery { repository.apply(REPLY_ACCOUNT, any()) } throws IllegalStateException("db")
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.applyPreview(REPLY_ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.DATABASE, error(vm))
        assertNull(vm.replyPeer.value)
    }

    @Test fun applyingForAnotherAccountClearsTheReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.applyPreview(REPLY_ACCOUNT + 1); runCurrent()
        assertNull(vm.replyPeer.value)
    }

    @Test fun forgettingThatPeerClearsTheReplyPeerButForgettingAnotherDoesNot() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val other = store.add("Other", PairingSecret.generate(), PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        vm.forgetPeer(other.peerId)
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
        vm.forgetPeer(peer.peerId)
        assertNull(vm.replyPeer.value)
    }

    @Test fun startingAnotherTaskClearsTheReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        vm.startSend(REPLY_ACCOUNT); runCurrent()
        assertNull(vm.replyPeer.value)
    }

    @Test fun aRejectedStartWhileBusyKeepsTheReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        // The preview is showing, so a second start is refused by the busy guard and must not wipe the context.
        vm.startSend(REPLY_ACCOUNT); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
    }

    @Test fun clearingTheViewModelClearsTheReplyPeer() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.callOnCleared()
        assertNull(vm.replyPeer.value)
    }

    // ---- Stop and wait to receive back ----

    @Test fun waitingForAReplyClosesTheSessionAndStartsFindingPeers() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(REPLY_ACCOUNT, peer.peerId); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Sending)
        vm.waitForReply()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertFalse(engine.isRunning)
        assertEquals(1, nsd.unregisters)
        assertEquals(1, nsd.discoveries)
        assertEquals(1, engine.tokenCalls)
        assertEquals(listOf(peer.peerId), store.list().map { it.peerId })
    }

    @Test fun waitingForAReplyDoesNothingWithoutAPairedSession() = runTest(dispatcher) {
        val vm = vm()
        vm.waitForReply()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertEquals(0, nsd.discoveries)
        vm.startSend(REPLY_ACCOUNT); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Sending)
        vm.waitForReply()
        assertTrue(vm.state.value is LocalSyncUiState.Sending)
        assertTrue(engine.isRunning)
    }

    @Test fun waitingForAReplyWithoutTheLanPermissionClosesThenReportsIt() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(REPLY_ACCOUNT, peer.peerId); runCurrent()
        lanGranted = false
        vm.waitForReply()
        assertFalse(engine.isRunning)
        assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
        assertEquals(0, nsd.discoveries)
    }

    // ---- Send back ----

    @Test fun replyingServesAFreshSnapshotUnderTheStoredSecretAndShowsNoSecret() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.applyPreview(REPLY_ACCOUNT); runCurrent()
        coVerify(exactly = 0) { repository.snapshot(any()) }
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        val sending = vm.state.value as LocalSyncUiState.Sending
        assertNull(sending.secretHex)
        assertEquals(peer.peerId, sending.pairedPeerId)
        coVerify(exactly = 1) { repository.snapshot(REPLY_ACCOUNT) }
        coVerify(exactly = 1) { repository.apply(any(), any()) }
        assertEquals(listOf(peer.peerId), store.list().map { it.peerId })
        assertTrue(SyncEnvelopeCrypto.decrypt(requireNotNull(engine.lastCiphertext), peerSecret).isNotEmpty())
        assertNull(vm.replyPeer.value)
    }

    @Test fun replyingAfterUserCancelAppliesNothingAndStillServes() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Sending)
        coVerify(exactly = 0) { repository.apply(any(), any()) }
        coVerify(exactly = 1) { repository.snapshot(REPLY_ACCOUNT) }
    }

    @Test fun replyingIsRefusedWhenThePeerNeedsRepair() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        wrapper.keyPresent = false
        vm.refreshPeers()
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.PAIRING_BROKEN, error(vm))
        assertNull(vm.replyPeer.value)
        assertEquals(0, engine.tokenCalls)
        // A peer that cannot be opened is refused up front: the database is not even snapshotted for it.
        coVerify(exactly = 0) { repository.snapshot(any()) }
    }

    @Test fun replyingForAnotherAccountOrWithoutAReplyPeerDoesNothing() = runTest(dispatcher) {
        val vm = vm()
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        vm.replyToPeer(REPLY_ACCOUNT + 1); runCurrent()
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertEquals(0, engine.tokenCalls)
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
    }

    @Test fun replyingWhileBusyStartsNothingAndKeepsThePreview() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        assertEquals(0, engine.tokenCalls)
        assertEquals(ReplyPeer(REPLY_ACCOUNT, peer.peerId), vm.replyPeer.value)
    }

    @Test fun replyingWhileBusyWithABrokenPeerDoesNotOverwriteThePreviewWithAnError() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        wrapper.keyPresent = false
        vm.refreshPeers()
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
    }

    @Test fun replyingWithoutTheLanPermissionReportsItAndOpensNothing() = runTest(dispatcher) {
        val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPeerNow(peer.peerId); runCurrent()
        vm.cancelPreview()
        lanGranted = false
        vm.replyToPeer(REPLY_ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
        assertEquals(0, engine.tokenCalls)
    }
}
