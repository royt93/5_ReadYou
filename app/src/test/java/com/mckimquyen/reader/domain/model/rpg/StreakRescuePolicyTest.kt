package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakRescuePolicyTest {
    private val today = 20_000L

    @Test
    fun returnsNull_whenStreakIsBelowMinimum() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 2, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
        assertNull(StreakRescuePolicy.evaluate(streakDays = 0, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsNull_whenShieldIsAlreadyActive() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 3, shieldActive = true, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsNull_whenNeverReadBefore() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = 0L, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = -1L, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsNull_whenStreakIsStillAlive() {
        // Read today: no break
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
        // Read yesterday: alive, reading today would increment
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 1, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun returnsOffer_whenGapIsTwoDaysOrMore() {
        val offer = StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 2, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L)
        assertEquals(StreakRescueOffer(streakDays = 5, missedDays = 2), offer)

        val longGap = StreakRescuePolicy.evaluate(streakDays = 14, lastReadEpochDay = today - 10, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L)
        assertEquals(StreakRescueOffer(streakDays = 14, missedDays = 10), longGap)
    }

    @Test
    fun returnsNull_whenAlreadyPromptedToday() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = today))
    }

    @Test
    fun returnsOffer_whenPromptedYesterday_andStillUnreadToday() {
        val offer = StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today - 3, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = today - 1)
        assertEquals(StreakRescueOffer(streakDays = 5, missedDays = 3), offer)
    }

    @Test
    fun returnsNull_whenClockSkewGoesBackward() {
        assertNull(StreakRescuePolicy.evaluate(streakDays = 5, lastReadEpochDay = today + 2, shieldActive = false, todayEpochDay = today, lastPromptEpochDay = 0L))
    }

    @Test
    fun epochDay_matchesMillisPerDay() {
        assertEquals(0L, StreakRescuePolicy.epochDay(0L))
        assertEquals(1L, StreakRescuePolicy.epochDay(StreakRescuePolicy.MILLIS_PER_DAY))
        assertEquals(20_000L, StreakRescuePolicy.epochDay(20_000L * StreakRescuePolicy.MILLIS_PER_DAY + 12_345L))
    }
}
