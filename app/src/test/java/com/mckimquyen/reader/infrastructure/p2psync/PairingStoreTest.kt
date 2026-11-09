package com.mckimquyen.reader.infrastructure.p2psync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.PairingSecret
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Stand-in for the Keystore: reversible but visibly different from the input, and able to "lose its key". */
private class FakeWrapper : SecretWrapper {
    var keyPresent = true
    override fun wrap(plain: ByteArray): ByteArray = byteArrayOf(0x7F) + ByteArray(plain.size) { (plain[it].toInt() xor 0x5A).toByte() }
    override fun unwrap(wrapped: ByteArray): ByteArray? {
        if (!keyPresent || wrapped.isEmpty() || wrapped[0] != 0x7F.toByte()) return null
        return ByteArray(wrapped.size - 1) { (wrapped[it + 1].toInt() xor 0x5A).toByte() }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PairingStoreTest {
    private lateinit var context: Context
    private val wrapper = FakeWrapper()
    private var now = 1_000L
    private val secretA = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
    private val secretB = PairingSecret.fromHex("ffeeddccbbaa99887766554433221100")

    private fun store() = PairingStore(context, wrapper) { now }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PairingStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() {
        context.getSharedPreferences(PairingStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun addThenListShowsTheEntryWithItsStatusAndLabel() {
        val added = store().add("Living room tablet", secretA, PairingStatus.UNCONFIRMED)
        val listed = store().list()
        assertEquals(listOf(added), listed)
        assertEquals("Living room tablet", listed.single().label)
        assertEquals(PairingStatus.UNCONFIRMED, listed.single().status)
        assertEquals(1_000L, listed.single().createdAtMillis)
        assertFalse(listed.single().needsRepair)
    }

    @Test fun secretRoundTripsThroughTheWrapper() {
        val peer = store().add("Phone", secretA, PairingStatus.CONFIRMED)
        assertEquals(secretA.hex, store().secretOf(peer.peerId)?.hex)
    }

    @Test fun theStoredFileNeverContainsThePlainSecret() {
        store().add("Phone", secretA, PairingStatus.CONFIRMED)
        val file = File(context.applicationInfo.dataDir, "shared_prefs/${PairingStore.FILE_NAME}.xml")
        assertTrue("store file must exist: $file", file.exists())
        val text = file.readText()
        assertFalse(text.contains(secretA.hex))
        assertFalse(text.contains(secretA.hex.uppercase()))
    }

    @Test fun peerIdsAreThirtyTwoLowercaseHexAndUnique() {
        val ids = (1..5).map { store().add("Device $it", secretA, PairingStatus.UNCONFIRMED).peerId }
        assertEquals(5, ids.toSet().size)
        ids.forEach { id ->
            assertEquals(32, id.length)
            assertTrue(id.all { it in "0123456789abcdef" })
        }
    }

    @Test fun labelIsTrimmed() {
        assertEquals("Phone", store().add("  Phone  ", secretA, PairingStatus.CONFIRMED).label)
    }

    @Test fun blankOverlongOrControlCharacterLabelsAreRejected() {
        listOf("", "   ", "x".repeat(PairingStore.MAX_LABEL_CHARS + 1), "bad\nlabel", "bad\u0000label").forEach { bad ->
            assertTrue(bad, runCatching { store().add(bad, secretA, PairingStatus.UNCONFIRMED) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(store().list().isEmpty())
    }

    @Test fun aLabelOfExactlyTheLimitIsAccepted() {
        assertEquals(PairingStore.MAX_LABEL_CHARS, store().add("x".repeat(PairingStore.MAX_LABEL_CHARS), secretA, PairingStatus.CONFIRMED).label.length)
    }

    @Test fun forgetRemovesOnlyThatEntry() {
        val a = store().add("A", secretA, PairingStatus.CONFIRMED)
        val b = store().add("B", secretB, PairingStatus.CONFIRMED)
        store().forget(a.peerId)
        assertEquals(listOf(b.peerId), store().list().map { it.peerId })
        assertNull(store().secretOf(a.peerId))
    }

    @Test fun markConfirmedChangesOnlyThatEntry() {
        val a = store().add("A", secretA, PairingStatus.UNCONFIRMED)
        val b = store().add("B", secretB, PairingStatus.UNCONFIRMED)
        store().markConfirmed(a.peerId)
        val byId = store().list().associateBy { it.peerId }
        assertEquals(PairingStatus.CONFIRMED, byId.getValue(a.peerId).status)
        assertEquals(PairingStatus.UNCONFIRMED, byId.getValue(b.peerId).status)
    }

    @Test fun whenTheKeyIsLostTheEntryNeedsRepairAndNothingIsThrown() {
        val peer = store().add("Phone", secretA, PairingStatus.CONFIRMED)
        wrapper.keyPresent = false
        val listed = store().list().single()
        assertTrue(listed.needsRepair)
        assertEquals(peer.peerId, listed.peerId)
        assertNull(store().secretOf(peer.peerId))
    }

    @Test fun aRepairNeededEntryCanStillBeForgotten() {
        val peer = store().add("Phone", secretA, PairingStatus.CONFIRMED)
        wrapper.keyPresent = false
        store().forget(peer.peerId)
        assertTrue(store().list().isEmpty())
    }

    @Test fun moreThanTheMaximumNumberOfPeersIsRejected() {
        repeat(PairingStore.MAX_PEERS) { store().add("Device $it", secretA, PairingStatus.UNCONFIRMED) }
        assertTrue(runCatching { store().add("One too many", secretA, PairingStatus.UNCONFIRMED) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(PairingStore.MAX_PEERS, store().list().size)
    }

    @Test fun entriesSurviveANewStoreInstance() {
        val peer = store().add("Phone", secretA, PairingStatus.CONFIRMED)
        assertEquals(listOf(peer), PairingStore(context, wrapper) { now }.list())
    }

    @Test fun listIsOrderedByCreationTimeThenId() {
        now = 3_000L; val late = store().add("Late", secretA, PairingStatus.CONFIRMED)
        now = 1_000L; val early = store().add("Early", secretB, PairingStatus.CONFIRMED)
        assertEquals(listOf(early.peerId, late.peerId), store().list().map { it.peerId })
    }

    @Test fun secretOfAnUnknownIdIsNull() {
        assertNull(store().secretOf("0".repeat(32)))
    }

    @Test fun twoPeersWithTheSameSecretStayDistinctEntries() {
        val a = store().add("A", secretA, PairingStatus.CONFIRMED)
        val b = store().add("B", secretA, PairingStatus.CONFIRMED)
        assertNotEquals(a.peerId, b.peerId)
        assertEquals(2, store().list().size)
    }

    @Suppress("unused") private fun unusedTypeReference(peer: PairedPeer) = peer
}
