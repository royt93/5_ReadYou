package com.mckimquyen.reader.infrastructure.p2psync

import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.p2psync.PairingSecret
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import com.mckimquyen.reader.domain.p2psync.SyncSessionTransport
import com.mckimquyen.reader.infrastructure.mirror.LanAddressProvider
import com.mckimquyen.reader.infrastructure.mirror.MirrorServer
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.page.p2psync.LocalSyncUiState
import com.mckimquyen.reader.ui.page.p2psync.LocalSyncViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * EXC-05e: the two roles of the auto receive on two REAL phones (real NSD, real Keystore, real Room read-only, real Wi-Fi). One phone runs
 * [servePaired] (role "send"), the other runs the same calls the sync screen makes (role "receive"). The roles are picked by the instrument
 * argument `role`; the shared pairing secret comes in as `secret`, so no code is typed in a screen (pairing through the screen was checked in
 * EXC-05b and 05c). The receive role never applies: it only waits for the preview, times it, and checks that its own database is unchanged.
 */
class AutoReceiveTwoPhonesDeviceTest {
    private lateinit var context: Context
    private lateinit var args: Bundle
    private lateinit var store: PairingStore
    private lateinit var vm: LocalSyncViewModel
    private val addedPeerIds = ArrayList<String>()

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        args = InstrumentationRegistry.getArguments()
        store = PairingStore(context, KeystoreSecretWrapper())
        vm = runBlocking(Dispatchers.Main) { newViewModel() }
    }

    @After fun tearDown() {
        addedPeerIds.forEach { store.forget(it) }
        runBlocking(Dispatchers.Main) { vm.onScreenStopped(); vm.callOnCleared() }
    }

    private fun newViewModel(): LocalSyncViewModel {
        val app = context.applicationContext as RApp
        val repository = LocalSyncRepository(app.androidDatabase)
        return LocalSyncViewModel(
            repository, SyncSessionTransport(), MirrorServer(), LanAddressProvider(context),
            Dispatchers.Default, Dispatchers.IO, store, PeerDiscovery(AndroidNsdPlatform(context)), AndroidLanGate(context),
        )
    }

    private fun pairWith(label: String, secretHex: String): String {
        val peer = store.add(label, PairingSecret.fromHex(secretHex), PairingStatus.CONFIRMED)
        addedPeerIds += peer.peerId
        return peer.peerId
    }

    /** Rows of the real Room, counted read-only through the open helper: articles and feeds. */
    private fun roomCounts(): Pair<Int, Int> {
        val db = (context.applicationContext as RApp).androidDatabase.openHelper.readableDatabase
        fun count(table: String) = db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        return count("article") to count("feed")
    }

    private fun waitFor(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) { if (condition()) return true; Thread.sleep(POLL_MILLIS) }
        return condition()
    }

    private fun flagFile(name: String) = File(context.cacheDir, "exc05e-$name")

    @Test fun sendRole_servesTheStoredPairing_untilTheStopFlag() {
        assumeTrue("role must be send", args.getString("role") == "send")
        val peerId = pairWith("exc05e-peer", requireNotNull(args.getString("secret")))
        flagFile("stop").delete()
        runBlocking(Dispatchers.Main) { vm.servePaired(context.currentAccountId, peerId) }
        assertTrue("the send session must start", waitFor(START_TIMEOUT_MS) { vm.state.value is LocalSyncUiState.Sending })
        flagFile("ready").writeText("1")
        waitFor(HOLD_TIMEOUT_MS) { flagFile("stop").exists() }
        flagFile("ready").delete()
    }

    @Test fun receiveRole_oneDeviceOneBroadcaster_showsAPreviewByItself_andWritesNothing() {
        assumeTrue("role must be receive", args.getString("role") == "receive")
        pairWith("exc05e-peer", requireNotNull(args.getString("secret")))
        val before = roomCounts()
        val started = System.currentTimeMillis()
        runBlocking(Dispatchers.Main) { vm.refreshPeers(); vm.startFindingPeers() }
        // The same calls the sync screen's LaunchedEffect makes, every time the peers or the state change.
        val gotPreview = waitFor(PREVIEW_TIMEOUT_MS) {
            runBlocking(Dispatchers.Main) { vm.maybeAutoReceive(context.currentAccountId, isLocal = true) }
            vm.state.value is LocalSyncUiState.Preview
        }
        val elapsed = System.currentTimeMillis() - started
        println("EXC05E_PREVIEW_MS=$elapsed")
        assertTrue("a preview must appear by itself, state=${vm.state.value}", gotPreview)
        runBlocking(Dispatchers.Main) { vm.cancelPreview() }
        assertEquals("the receiving phone's database must not change before Apply", before, roomCounts())
    }

    @Test fun receiveRole_twoPairedDevices_doesNotReceiveByItself() {
        assumeTrue("role must be receive-two", args.getString("role") == "receive-two")
        pairWith("exc05e-peer-a", requireNotNull(args.getString("secret")))
        pairWith("exc05e-peer-b", PairingSecret.generate().hex)
        runBlocking(Dispatchers.Main) { vm.refreshPeers(); vm.startFindingPeers() }
        val gotPreview = waitFor(NEGATIVE_WAIT_MS) {
            runBlocking(Dispatchers.Main) { vm.maybeAutoReceive(context.currentAccountId, isLocal = true) }
            vm.state.value !is LocalSyncUiState.Idle
        }
        assertTrue("two paired devices: nothing may start by itself, state=${vm.state.value}", !gotPreview)
    }

    private companion object {
        const val POLL_MILLIS = 100L
        const val START_TIMEOUT_MS = 15_000L
        const val HOLD_TIMEOUT_MS = 120_000L
        const val PREVIEW_TIMEOUT_MS = 30_000L
        const val NEGATIVE_WAIT_MS = 8_000L
    }
}
