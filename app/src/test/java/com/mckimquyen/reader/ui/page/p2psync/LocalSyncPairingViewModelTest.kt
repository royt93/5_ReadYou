package com.mckimquyen.reader.ui.page.p2psync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.mirror.MirrorToken
import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlan
import com.mckimquyen.reader.domain.p2psync.NsdTxtCodec
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
import io.mockk.verify
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

private const val ACCOUNT = 1
private const val TTL = 10_000_000L

private class PairFakeEngine : SyncSessionEngine {
    var tokenCalls = 0
    var randomCalls = 0
    var lastToken: String? = null
    var lastCiphertext: ByteArray? = null
    var stops = 0
    override var isRunning = false
    override fun startSync(bindAddress: InetAddress, ciphertext: ByteArray): MirrorStartResult {
        randomCalls++
        isRunning = true
        return MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), TTL))
    }
    override fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String): MirrorStartResult {
        tokenCalls++
        lastToken = tokenValue
        lastCiphertext = ciphertext.copyOf()
        isRunning = true
        return MirrorStartResult.Started(8080, MirrorToken(tokenValue, TTL))
    }
    override fun stop() { stops++; isRunning = false }
}

private class PairFakeLan : LanAddressSource {
    val losses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override fun current(): InetAddress? = InetAddress.getByName("192.168.1.20")
    override fun networkLosses(): Flow<Unit> = losses
}

private class PairFakeNsd : NsdPlatform {
    var registered: Triple<String, Int, Map<String, ByteArray>>? = null
    var unregisters = 0
    var discoveries = 0
    var stopDiscoveries = 0
    override fun register(serviceName: String, port: Int, attributes: Map<String, ByteArray>, onResult: (Boolean) -> Unit) {
        registered = Triple(serviceName, port, attributes); onResult(true)
    }
    override fun unregister() { unregisters++; registered = null }
    override fun discover(onFound: (String, String?, Int, Map<String, ByteArray?>) -> Unit, onLost: (String) -> Unit, onFailed: () -> Unit) { discoveries++ }
    override fun stopDiscovery() { stopDiscoveries++ }
}

private class PairFakeWrapper : SecretWrapper {
    var keyPresent = true
    override fun wrap(plain: ByteArray) = byteArrayOf(0x7F) + ByteArray(plain.size) { (plain[it].toInt() xor 0x5A).toByte() }
    override fun unwrap(wrapped: ByteArray): ByteArray? =
        if (!keyPresent || wrapped.isEmpty()) null else ByteArray(wrapped.size - 1) { (wrapped[it + 1].toInt() xor 0x5A).toByte() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSyncPairingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = PairFakeEngine()
    private val lan = PairFakeLan()
    private val nsd = PairFakeNsd()
    private val wrapper = PairFakeWrapper()
    private val repository = mockk<LocalSyncRepository>()
    private val transport = mockk<SyncSessionTransport>()
    private lateinit var store: PairingStore
    private lateinit var discovery: PeerDiscovery
    private var lanGranted = true

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
        coEvery { repository.snapshot(ACCOUNT) } returns outgoing
        coEvery { repository.preview(ACCOUNT, any()) } returns plan
        coEvery { repository.apply(ACCOUNT, any()) } returns LocalSyncApplyResult(0, 1, 1, 0, 1)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(PairingStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun vm() = LocalSyncViewModel(repository, transport, engine, lan, dispatcher, dispatcher, store, discovery) { lanGranted }
        .also { it.clock = { 1_000L } }

    private fun error(vm: LocalSyncViewModel) = (vm.state.value as LocalSyncUiState.Error).kind

    @Test fun pairingANewDeviceStoresAnUnconfirmedEntryBeforeServing() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Living room tablet"); runCurrent()
        val peer = store.list().single()
        assertEquals("Living room tablet", peer.label)
        assertEquals(PairingStatus.UNCONFIRMED, peer.status)
        assertTrue(vm.state.value is LocalSyncUiState.Sending)
        assertEquals(1, engine.tokenCalls)
    }

    @Test fun theServedTokenIsDerivedFromTheStoredSecretAndTheAdvertisedNonceNotRandom() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        val secret = store.secretOf(store.list().single().peerId)!!
        val nonce = NsdTxtCodec.decode(nsd.registered!!.third.mapValues { it.value })!!
        assertEquals(SessionTokenDerivation.derive(secret, nonce), engine.lastToken)
        assertEquals(0, engine.randomCalls)
    }

    @Test fun theAdvertisedRecordCarriesOnlyTheVersionAndTheNonce() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        val attrs = nsd.registered!!.third
        assertEquals(setOf("v", "n"), attrs.keys)
        val sending = vm.state.value as LocalSyncUiState.Sending
        val text = attrs.values.joinToString("") { String(it, Charsets.ISO_8859_1) }
        assertFalse(text.contains(requireNotNull(sending.secretHex)))
        assertFalse(text.contains(engine.lastToken!!))
    }

    @Test fun theServedEnvelopeOpensWithTheStoredSecret() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        val secret = store.secretOf(store.list().single().peerId)!!
        val plain = SyncEnvelopeCrypto.decrypt(engine.lastCiphertext!!, secret)
        assertEquals(outgoing, (SyncPackageCodec.decodeAndValidate(plain) as com.mckimquyen.reader.domain.p2psync.SyncPackageResult.Valid).data)
    }

    @Test fun blankLabelIsRejectedAndNothingIsStoredOrServed() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "   "); runCurrent()
        assertTrue(store.list().isEmpty())
        assertEquals(0, engine.tokenCalls)
        assertEquals(LocalSyncError.PAIRING_LABEL, error(vm))
    }

    @Test fun withoutLanPermissionNothingIsStoredServedAdvertisedOrOpened() = runTest(dispatcher) {
        lanGranted = false
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
        assertTrue(store.list().isEmpty())
        assertEquals(0, engine.tokenCalls)
        assertNull(nsd.registered)
    }

    @Test fun withoutLanPermissionTheManualSendAlsoOpensNoSocket() = runTest(dispatcher) {
        lanGranted = false
        val vm = vm()
        vm.startSend(ACCOUNT); runCurrent()
        assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
        assertEquals(0, engine.randomCalls)
    }

    @Test fun withoutLanPermissionManualReceiveFetchesNothing() = runTest(dispatcher) {
        lanGranted = false
        val vm = vm()
        vm.loadPreview(ACCOUNT, "http://192.168.1.30:4321/s/${"c".repeat(32)}", "0".repeat(32)); runCurrent()
        assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
        verify(exactly = 0) { transport.fetch(any()) }
    }

    @Test fun stoppingTheSessionStopsAdvertisingToo() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        vm.stopSend()
        assertEquals(1, nsd.unregisters)
        assertFalse(engine.isRunning)
    }

    @Test fun losingTheNetworkStopsAdvertising() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        lan.losses.tryEmit(Unit); runCurrent()
        assertEquals(1, nsd.unregisters)
        assertEquals(LocalSyncError.NO_NETWORK, error(vm))
    }

    @Test fun theSessionExpiringStopsAdvertising() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        advanceTimeBy(TTL + 1)
        assertEquals(1, nsd.unregisters)
        assertEquals(LocalSyncError.EXPIRED, error(vm))
    }

    @Test fun leavingTheScreenStopsAdvertisingAndDiscovery() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        vm.startFindingPeers(); runCurrent()
        vm.onScreenStopped()
        assertEquals(1, nsd.unregisters)
        assertEquals(1, nsd.stopDiscoveries)
    }

    @Test fun clearingTheViewModelStopsAdvertisingAndDiscovery() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        vm.startFindingPeers(); runCurrent()
        vm.callOnCleared()
        assertEquals(1, nsd.unregisters)
        assertEquals(1, nsd.stopDiscoveries)
    }

    @Test fun findingPeersNeedsTheLanPermission() = runTest(dispatcher) {
        lanGranted = false
        val vm = vm()
        vm.startFindingPeers(); runCurrent()
        assertEquals(0, nsd.discoveries)
        assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
    }

    @Test fun receivingFromAPairedDeviceComputesTheTokenAndFetchesItsUrl() = runTest(dispatcher) {
        val secret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
        val peer = store.add("Phone", secret, PairingStatus.CONFIRMED)
        val nonce = SessionTokenDerivation.newNonce()
        val token = SessionTokenDerivation.derive(secret, nonce)
        val url = "http://192.168.1.30:4321/s/$token"
        every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), secret)
        val vm = vm()
        vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        verify(exactly = 1) { transport.fetch(url) }
    }

    @Test fun aSuccessfulDecryptConfirmsThePairingOnlyAfterwards() = runTest(dispatcher) {
        val secret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
        val peer = store.add("Phone", secret, PairingStatus.UNCONFIRMED)
        val nonce = SessionTokenDerivation.newNonce()
        val url = "http://192.168.1.30:4321/s/${SessionTokenDerivation.derive(secret, nonce)}"
        every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), secret)
        val vm = vm()
        vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
        assertEquals(PairingStatus.CONFIRMED, store.list().single().status)
    }

    @Test fun aFailedDecryptNeverConfirmsAndWritesNothing() = runTest(dispatcher) {
        val secret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
        val peer = store.add("Phone", secret, PairingStatus.UNCONFIRMED)
        val nonce = SessionTokenDerivation.newNonce()
        val url = "http://192.168.1.30:4321/s/${SessionTokenDerivation.derive(secret, nonce)}"
        every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), PairingSecret.fromHex("f".repeat(32)))
        val vm = vm()
        vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
        assertEquals(LocalSyncError.PACKAGE, error(vm))
        assertEquals(PairingStatus.UNCONFIRMED, store.list().single().status)
        coVerify(exactly = 0) { repository.apply(any(), any()) }
    }

    // ---- serving again to an already paired device ----

    private val pairedSecret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")

    @Test fun servingAPairedDeviceReusesItsStoredSecretAndAddsNoEntry() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Sending)
        assertEquals(listOf(peer), store.list())
        val nonce = NsdTxtCodec.decode(nsd.registered!!.third.mapValues { it.value })!!
        assertEquals(SessionTokenDerivation.derive(pairedSecret, nonce), engine.lastToken)
        assertEquals(1, engine.tokenCalls)
    }

    @Test fun theServedEnvelopeOpensWithTheStoredSecretOfTheChosenDevice() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        store.add("Other", PairingSecret.generate(), PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        val plain = SyncEnvelopeCrypto.decrypt(engine.lastCiphertext!!, pairedSecret)
        assertTrue(plain.isNotEmpty())
    }

    @Test fun servingAgainNeverShowsTheSecretOnScreen() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        val sending = vm.state.value as LocalSyncUiState.Sending
        assertNull(sending.secretHex)
        assertEquals(peer.peerId, sending.pairedPeerId)
    }

    @Test fun servingDoesNotChangeTheStatusOfTheEntry() = runTest(dispatcher) {
        val unconfirmed = store.add("A", pairedSecret, PairingStatus.UNCONFIRMED)
        val vm = vm()
        vm.servePaired(ACCOUNT, unconfirmed.peerId); runCurrent()
        assertEquals(PairingStatus.UNCONFIRMED, store.list().single().status)
    }

    @Test fun everySessionAdvertisesAFreshNonce() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        val first = NsdTxtCodec.decode(nsd.registered!!.third.mapValues { it.value })!!
        vm.stopSend(); runCurrent()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        val second = NsdTxtCodec.decode(nsd.registered!!.third.mapValues { it.value })!!
        assertFalse(first.contentEquals(second))
    }

    @Test fun anUnknownDeviceShowsPairingBrokenAndStartsNothing() = runTest(dispatcher) {
        val vm = vm()
        vm.servePaired(ACCOUNT, "0".repeat(32)); runCurrent()
        assertEquals(LocalSyncError.PAIRING_BROKEN, error(vm))
        assertEquals(0, engine.tokenCalls)
        assertNull(nsd.registered)
    }

    @Test fun aDeviceWhoseKeyIsLostShowsPairingBrokenAndStartsNothing() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        wrapper.keyPresent = false
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        assertEquals(LocalSyncError.PAIRING_BROKEN, error(vm))
        assertEquals(0, engine.tokenCalls)
    }

    @Test fun servingNeedsTheLanPermission() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        lanGranted = false
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
        assertEquals(0, engine.tokenCalls)
        assertNull(nsd.registered)
    }

    @Test fun forgettingTheDeviceBeingServedStopsTheSession() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        vm.forgetPeer(peer.peerId)
        assertEquals(LocalSyncUiState.Idle, vm.state.value)
        assertTrue(engine.stops >= 1)
        assertEquals(emptyList<Any>(), store.list())
    }

    @Test fun stoppingAServedSessionStopsAdvertisingToo() = runTest(dispatcher) {
        val peer = store.add("Tablet", pairedSecret, PairingStatus.CONFIRMED)
        val vm = vm()
        vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
        vm.stopSend()
        assertEquals(1, nsd.unregisters)
    }

    // ---- first pairing on the receiving phone ----

    private val manualUrl = "http://192.168.1.30:4321/s/${"c".repeat(32)}"
    private val manualSecret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")

    @Test fun aManualReceiveWithALabelStoresAConfirmedEntryOnceTheEnvelopeOpens() = runTest(dispatcher) {
        every { transport.fetch(manualUrl) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), manualSecret)
        val vm = vm()
        vm.loadPreview(ACCOUNT, manualUrl, manualSecret.hex, "Tablet"); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        val peer = store.list().single()
        assertEquals("Tablet", peer.label)
        assertEquals(PairingStatus.CONFIRMED, peer.status)
        assertEquals(manualSecret.hex, store.secretOf(peer.peerId)?.hex)
        assertEquals(listOf(peer), vm.pairedPeers.value)
    }

    @Test fun aManualReceiveWithoutALabelStoresNothing() = runTest(dispatcher) {
        every { transport.fetch(manualUrl) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), manualSecret)
        val vm = vm()
        vm.loadPreview(ACCOUNT, manualUrl, manualSecret.hex); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        assertEquals(emptyList<Any>(), store.list())
    }

    @Test fun aBlankLabelCountsAsNoLabel() = runTest(dispatcher) {
        every { transport.fetch(manualUrl) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), manualSecret)
        val vm = vm()
        vm.loadPreview(ACCOUNT, manualUrl, manualSecret.hex, "   "); runCurrent()
        assertTrue(vm.state.value is LocalSyncUiState.Preview)
        assertEquals(emptyList<Any>(), store.list())
    }

    @Test fun aWrongSecretWithALabelStoresNothing() = runTest(dispatcher) {
        every { transport.fetch(manualUrl) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), PairingSecret.fromHex("f".repeat(32)))
        val vm = vm()
        vm.loadPreview(ACCOUNT, manualUrl, manualSecret.hex, "Tablet"); runCurrent()
        assertEquals(LocalSyncError.PACKAGE, error(vm))
        assertEquals(emptyList<Any>(), store.list())
    }

    @Test fun anInvalidLabelIsRejectedBeforeAnythingIsFetched() = runTest(dispatcher) {
        val vm = vm()
        vm.loadPreview(ACCOUNT, manualUrl, manualSecret.hex, "x".repeat(61)); runCurrent()
        assertEquals(LocalSyncError.PAIRING_LABEL, error(vm))
        verify(exactly = 0) { transport.fetch(any()) }
        assertEquals(emptyList<Any>(), store.list())
    }

    @Test fun aFullStoreReportsTheLimitAndNeverShowsAPreview() = runTest(dispatcher) {
        repeat(PairingStore.MAX_PEERS) { store.add("P$it", PairingSecret.generate(), PairingStatus.CONFIRMED) }
        every { transport.fetch(manualUrl) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), manualSecret)
        val vm = vm()
        vm.loadPreview(ACCOUNT, manualUrl, manualSecret.hex, "One too many"); runCurrent()
        assertEquals(LocalSyncError.PAIRING_LIMIT, error(vm))
        assertEquals(PairingStore.MAX_PEERS, store.list().size)
    }

    @Test fun aBrokenPairingEntryShowsPairingBrokenAndOpensNoConnection() = runTest(dispatcher) {
        val peer = store.add("Phone", PairingSecret.fromHex("00112233445566778899aabbccddeeff"), PairingStatus.CONFIRMED)
        wrapper.keyPresent = false
        val vm = vm()
        vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, SessionTokenDerivation.newNonce()); runCurrent()
        assertEquals(LocalSyncError.PAIRING_BROKEN, error(vm))
        verify(exactly = 0) { transport.fetch(any()) }
    }

    @Test fun anUnknownPeerIdShowsPairingBroken() = runTest(dispatcher) {
        val vm = vm()
        vm.receiveFromPaired(ACCOUNT, "0".repeat(32), "192.168.1.30", 4321, SessionTokenDerivation.newNonce()); runCurrent()
        assertEquals(LocalSyncError.PAIRING_BROKEN, error(vm))
    }

    @Test fun aNonPrivateHostIsRefusedBeforeAnyFetch() = runTest(dispatcher) {
        val peer = store.add("Phone", PairingSecret.fromHex("00112233445566778899aabbccddeeff"), PairingStatus.CONFIRMED)
        val vm = vm()
        vm.receiveFromPaired(ACCOUNT, peer.peerId, "8.8.8.8", 4321, SessionTokenDerivation.newNonce()); runCurrent()
        assertEquals(LocalSyncError.URL, error(vm))
        verify(exactly = 0) { transport.fetch(any()) }
    }

    @Test fun forgettingAPeerRemovesItAndConfirmingMarksIt() = runTest(dispatcher) {
        val a = store.add("A", PairingSecret.fromHex("00112233445566778899aabbccddeeff"), PairingStatus.UNCONFIRMED)
        val b = store.add("B", PairingSecret.fromHex("ffeeddccbbaa99887766554433221100"), PairingStatus.UNCONFIRMED)
        val vm = vm()
        vm.confirmPeer(a.peerId)
        assertEquals(PairingStatus.CONFIRMED, store.list().first { it.peerId == a.peerId }.status)
        vm.forgetPeer(b.peerId)
        assertEquals(listOf(a.peerId), store.list().map { it.peerId })
        assertNotNull(vm.pairedPeers.value)
    }

    @Test fun forgettingThePeerBeingServedStopsTheSession() = runTest(dispatcher) {
        val vm = vm()
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        vm.forgetPeer(store.list().single().peerId)
        assertFalse(engine.isRunning)
        assertEquals(1, nsd.unregisters)
        assertTrue(store.list().isEmpty())
    }

    @Test fun bindFailureWhilePairingStopsAdvertisingAndKeepsTheEntryUnconfirmed() = runTest(dispatcher) {
        val failing = object : SyncSessionEngine by engine {
            override fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String) =
                MirrorStartResult.Failed(MirrorFailure.BIND)
        }
        val vm = LocalSyncViewModel(repository, transport, failing, lan, dispatcher, dispatcher, store, discovery) { true }
        vm.startPairedSend(ACCOUNT, "Tablet"); runCurrent()
        assertEquals(LocalSyncError.SERVER, error(vm))
        assertNull(nsd.registered)
    }
}
