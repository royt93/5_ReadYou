package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryDecayTest {
    private val week = CategoryDecay.WEEK_MILLIS

    @Test
    fun zeroXp_remainsZeroAtEveryAge() {
        listOf(0L, 6 * week, 7 * week, 21 * week).forEach { now ->
            assertEquals(0L, CategoryDecay.effectiveXp(0L, 0L, now))
        }
    }

    @Test
    fun lessThanAFullWeek_doesNotDecay() {
        assertEquals(100L, CategoryDecay.effectiveXp(100L, 0L, week - 1))
    }

    @Test
    fun eachCompletedWeek_multipliesByNinetyPercentAndFloors() {
        assertEquals(90L, CategoryDecay.effectiveXp(100L, 0L, week))
        assertEquals(81L, CategoryDecay.effectiveXp(100L, 0L, 2 * week))
        assertEquals(72L, CategoryDecay.effectiveXp(100L, 0L, 3 * week))
    }

    @Test
    fun exactBoundary_isOneWeek_butOneMillisecondBeforeIsZeroWeeks() {
        assertEquals(90L, CategoryDecay.effectiveXp(101L, 10L, 10L + week))
        assertEquals(101L, CategoryDecay.effectiveXp(101L, 10L, 10L + week - 1))
    }

    @Test
    fun negativeTimeDeltaAndFuturePracticeTimestamp_neverDecay() {
        assertEquals(101L, CategoryDecay.effectiveXp(101L, 10L, 9L))
        assertEquals(101L, CategoryDecay.effectiveXp(101L, week + 10L, 10L))
    }

    @Test
    fun rounding_isFloored_andNeverReturnsNegativeXp() {
        assertEquals(0L, CategoryDecay.effectiveXp(1L, 0L, week))
        assertEquals(0L, CategoryDecay.effectiveXp(1L, 0L, 100 * week))
    }

    @Test
    fun largeXp_doesNotOverflowOrExceedBaseXp() {
        val effective = CategoryDecay.effectiveXp(Long.MAX_VALUE, 0L, 4 * week)
        assertEquals(false, effective < 0L)
        assertEquals(true, effective <= Long.MAX_VALUE)
        assertEquals(true, effective < Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, CategoryDecay.effectiveXp(Long.MAX_VALUE, 0L, 0L))
    }

    @Test
    fun qualifyingPractice_resetsDecayByUsingTheNewPracticeTimestamp() {
        val practicedNow = 20 * week
        assertEquals(100L, CategoryDecay.effectiveXp(100L, practicedNow, practicedNow))
        assertEquals(100L, CategoryDecay.effectiveXp(100L, practicedNow, practicedNow + week - 1))
    }
}
