package com.mckimquyen.reader.infrastructure.p2psync

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
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
import org.junit.Before
import org.junit.Test

/**
 * EXC-05e (ONE phone variant): the auto receive with the real NSD, real Keystore, real engine, real Room (read-only) and the real Wi-Fi of one
 * device. Two sync view models run side by side: one SERVES a paired device (what the other phone would do), the other is the sync screen's
 * receive side and makes the same calls the screen's LaunchedEffect makes. Each has its OWN pairing file (the context renames the file), so
 * the app's real pairing store is never touched. It proves the logic and the NSD/network path under the screen; it is NOT two phones, so the
 * measured time has no radio hop between two devices. The receive side never applies: it times the preview and checks Room is unchanged.
 */
class AutoReceiveTwoPhonesDeviceTest {
    private lateinit var context: Context
    private lateinit var senderStore: PairingStore
    private lateinit var receiverStore: PairingStore
    private lateinit var sender: LocalSyncViewModel
    private lateinit var receiver: LocalSyncViewModel

    /** A context that gives each store its own preferences file, so the real "pairing_store" file is left alone. */
    private class RenamedPrefsContext(base: Context, private val prefix: String) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences(prefix + name, mode)
    }

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        senderStore = PairingStore(RenamedPrefsContext(context, SENDER_PREFIX), KeystoreSecretWrapper())
        receiverStore = PairingStore(RenamedPrefsContext(context, RECEIVER_PREFIX), KeystoreSecretWrapper())
        sender = runBlocking(Dispatchers.Main) { newViewModel(senderStore) }
        receiver = runBlocking(Dispatchers.Main) { newViewModel(receiverStore) }
    }

    @After fun tearDown() {
        runBlocking(Dispatchers.Main) {
            receiver.onScreenStopped(); receiver.callOnCleared()
            sender.onScreenStopped(); sender.callOnCleared()
        }
        // The two private pairing files are the only things this test wrote; remove them.
        context.deleteSharedPreferences(SENDER_PREFIX + PairingStore.FILE_NAME)
        context.deleteSharedPreferences(RECEIVER_PREFIX + PairingStore.FILE_NAME)
    }

    private fun newViewModel(store: PairingStore): LocalSyncViewModel {
        val app = context.applicationContext as RApp
        return LocalSyncViewModel(
            LocalSyncRepository(app.androidDatabase), SyncSessionTransport(), MirrorServer(), LanAddressProvider(context),
            Dispatchers.Default, Dispatchers.IO, store, PeerDiscovery(AndroidNsdPlatform(context)), AndroidLanGate(context),
        )
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

    private fun startSending(secret: PairingSecret): Boolean {
        val peerId = senderStore.add("exc05e-sender-side", secret, PairingStatus.CONFIRMED).peerId
        runBlocking(Dispatchers.Main) { sender.servePaired(context.currentAccountId, peerId) }
        return waitFor(START_TIMEOUT_MS) { sender.state.value is LocalSyncUiState.Sending }
    }

    @Test fun oneDeviceOneBroadcaster_showsAPreviewByItself_andWritesNothing() {
        val secret = PairingSecret.generate()
        receiverStore.add("exc05e-receiver-side", secret, PairingStatus.CONFIRMED)
        val before = roomCounts()
        val started = System.currentTimeMillis()
        assertTrue("the send side must start, state=${sender.state.value}", startSending(secret))
        val sendingMs = System.currentTimeMillis() - started
        runBlocking(Dispatchers.Main) { receiver.refreshPeers(); receiver.startFindingPeers() }
        val foundAfter = System.currentTimeMillis()
        // The same call the sync screen's LaunchedEffect makes every time the peers or the state change.
        val gotPreview = waitFor(PREVIEW_TIMEOUT_MS) {
            runBlocking(Dispatchers.Main) { receiver.maybeAutoReceive(context.currentAccountId, isLocal = true) }
            receiver.state.value is LocalSyncUiState.Preview
        }
        val elapsed = System.currentTimeMillis() - started
        // println is not shown by a raw instrument run; a status bundle is, as INSTRUMENTATION_STATUS: exc05e_preview_ms=...
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            android.os.Bundle().apply {
                putString("exc05e_preview_ms", elapsed.toString())
                putString("exc05e_sender_ready_ms", sendingMs.toString())
                putString("exc05e_find_to_preview_ms", (started + elapsed - foundAfter).toString())
            },
        )
        assertTrue("a preview must appear by itself, state=${receiver.state.value}", gotPreview)
        runBlocking(Dispatchers.Main) { receiver.cancelPreview() }
        assertEquals("Room must not change before Apply", before, roomCounts())
    }

    @Test fun twoPairedDevices_doesNotReceiveByItself() {
        val secret = PairingSecret.generate()
        receiverStore.add("exc05e-receiver-side-a", secret, PairingStatus.CONFIRMED)
        receiverStore.add("exc05e-receiver-side-b", PairingSecret.generate(), PairingStatus.CONFIRMED)
        val before = roomCounts()
        assertTrue("the send side must start, state=${sender.state.value}", startSending(secret))
        runBlocking(Dispatchers.Main) { receiver.refreshPeers(); receiver.startFindingPeers() }
        // Wait until the broadcast is really seen, so "nothing happened" cannot mean "nothing was found".
        assertTrue("the broadcast must be discovered", waitFor(START_TIMEOUT_MS) { receiver.discovered.value.isNotEmpty() })
        val started = waitFor(NEGATIVE_WAIT_MS) {
            runBlocking(Dispatchers.Main) { receiver.maybeAutoReceive(context.currentAccountId, isLocal = true) }
            receiver.state.value !is LocalSyncUiState.Idle
        }
        assertTrue("two paired devices: nothing may start by itself, state=${receiver.state.value}", !started)
        assertEquals(before, roomCounts())
    }

    private companion object {
        const val SENDER_PREFIX = "exc05e_sender_"
        const val RECEIVER_PREFIX = "exc05e_receiver_"
        const val POLL_MILLIS = 100L
        const val START_TIMEOUT_MS = 15_000L
        const val PREVIEW_TIMEOUT_MS = 30_000L
        const val NEGATIVE_WAIT_MS = 6_000L
    }
}
