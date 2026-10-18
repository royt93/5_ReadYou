package com.mckimquyen.reader.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReadingStreakCalculatorTest {

    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun compute_emptyDays_returnsZeroes() {
        assertEquals(Streaks(0, 0), ReadingStreakCalculator.compute(emptySet(), today))
    }

    @Test
    fun compute_onlyToday_returnsOneForBoth() {
        val days = setOf(today)
        assertEquals(Streaks(1, 1), ReadingStreakCalculator.compute(days, today))
    }

    @Test
    fun compute_consecutiveRunEndingToday_countsRun() {
        val days = setOf(today.minusDays(3), today.minusDays(2), today.minusDays(1), today)
        assertEquals(Streaks(4, 4), ReadingStreakCalculator.compute(days, today))
    }

    @Test
    fun compute_noReadTodayButYesterday_preservesCurrentStreak() {
        val days = setOf(today.minusDays(2), today.minusDays(1))
        assertEquals(Streaks(2, 2), ReadingStreakCalculator.compute(days, today))
    }

    @Test
    fun compute_brokenStreak_currentIsZeroWhileLongestKept() {
        val days = setOf(today.minusDays(10), today.minusDays(9), today.minusDays(8))
        assertEquals(Streaks(0, 3), ReadingStreakCalculator.compute(days, today))
    }

    @Test
    fun compute_currentShorterThanEarlierLongest_reportsBoth() {
        val olderRun = (10..15).map { today.minusDays(it.toLong()) }
        val currentRun = listOf(today.minusDays(1), today)
        val days = (olderRun + currentRun).toSet()

        val streaks = ReadingStreakCalculator.compute(days, today)

        assertEquals(2, streaks.current)
        assertEquals(6, streaks.longest)
    }

    @Test
    fun compute_ignoresFutureDays() {
        val days = setOf(today, today.plusDays(1), today.plusDays(2))
        assertEquals(Streaks(1, 1), ReadingStreakCalculator.compute(days, today))
    }

    @Test
    fun compute_crossesYearBoundary() {
        val yearEnd = LocalDate.of(2027, 1, 2)
        val days = setOf(
            LocalDate.of(2026, 12, 30),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 1, 2),
        )
        assertEquals(Streaks(4, 4), ReadingStreakCalculator.compute(days, yearEnd))
    }
}
