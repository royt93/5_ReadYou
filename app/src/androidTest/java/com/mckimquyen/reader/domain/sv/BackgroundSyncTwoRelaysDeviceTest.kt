package com.mckimquyen.reader.domain.sv

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

private const val CLASS_TIMEOUT_SECONDS = 60L

/**
 * A Nostr source synced against TWO fake relays on 127.0.0.1 and the phone's real Room: the first relay is asked first, a silent first
 * relay must cost one timeout and then the second relay's note is stored. Only probe rows are written; cleanUp removes them.
 * NOT covered: a relay that answers half way (JVM only), a real network, more than two relays.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundSyncTwoRelaysDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)

    private lateinit var rig: BackgroundSyncRig
    private val second get() = requireNotNull(rig.secondNostrRelay)

    @Before
    fun setUp() {
        rig = BackgroundSyncRig(withSecondRelay = true)
        runBlocking {
            rig.cleanUp()
            rig.addProbeSources()
        }
    }

    @After
    fun tearDown() {
        try {
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    @Test
    fun aSilentFirstRelay_costsOneTimeout_thenTheSecondRelaysNoteIsStored() {
        val gate = CompletableDeferred<Unit>()
        rig.nostrRelay.holdAnswerUntil(gate)
        try {
            val startedNs = System.nanoTime()
            val result = runBlocking { rig.syncOnce(rig.nostrFeed) }
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
            assertEquals("the silent relay was asked", 1, rig.nostrRelay.requestsReceived())
            assertEquals("the second relay was asked after it", 1, second.requestsReceived())
            assertNotNull(result.outcome)
            assertEquals(1, result.inserted.size)
            assertTrue(result.inserted.single().link.startsWith("https://njump.me/note1"))
            assertEquals(1, rig.articleCountOf(rig.nostrFeed.id))
            assertTrue("did not wait for the silent relay: $elapsedMs ms", elapsedMs >= BackgroundSyncRig.SOCIAL_TIMEOUT_MS / 2)
            assertTrue("waited more than one timeout and a bit: $elapsedMs ms", elapsedMs < BackgroundSyncRig.SOCIAL_TIMEOUT_MS * 2)
        } finally {
            gate.complete(Unit)
        }
    }

    @Test
    fun aFirstRelayThatAnswers_meansTheSecondRelayIsNeverAsked() {
        val result = runBlocking { rig.syncOnce(rig.nostrFeed) }
        assertNotNull(result.outcome)
        assertEquals(1, result.inserted.size)
        assertEquals(1, rig.nostrRelay.requestsReceived())
        assertEquals("a relay that already gave a note stops the search", 0, second.requestsReceived())
    }

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeft() {
        runBlocking { rig.syncOnce(rig.nostrFeed) }
        assertTrue(rig.probeRowCount() > 0)
        runBlocking { rig.cleanUp() }
        assertEquals(0L, rig.probeRowCount())
    }
}
