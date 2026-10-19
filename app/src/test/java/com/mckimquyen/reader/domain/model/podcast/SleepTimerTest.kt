package com.mckimquyen.reader.domain.model.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SleepTimerTest {
    private var now = 0L
    private val timer = SleepTimer { now }

    @Test fun inactiveByDefault() {
        assertFalse(timer.isActive)
        assertNull(timer.remainingSec())
        assertFalse(timer.isExpired())
    }

    @Test fun startCountsDown() {
        timer.start(15)
        assertEquals(900, timer.remainingSec())
        now += 100_000
        assertEquals(800, timer.remainingSec())
        assertFalse(timer.isExpired())
    }

    @Test fun partialSecondRoundsUp() {
        timer.start(15)
        now += 1_500
        assertEquals(899, timer.remainingSec())
    }

    @Test fun expiresAtZeroAndNeverNegative() {
        timer.start(15)
        now += 15 * 60_000L + 5_000
        assertEquals(0, timer.remainingSec())
        assertTrue(timer.isExpired())
    }

    @Test fun cancelClears() {
        timer.start(30)
        timer.cancel()
        assertFalse(timer.isActive)
        assertNull(timer.remainingSec())
    }

    @Test fun unsupportedMinutesRejected() {
        try {
            timer.start(20)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            assertFalse(timer.isActive)
        }
    }

    @Test fun restartResetsEnd() {
        timer.start(15)
        now += 600_000
        timer.start(45)
        assertEquals(2700, timer.remainingSec())
    }
}
