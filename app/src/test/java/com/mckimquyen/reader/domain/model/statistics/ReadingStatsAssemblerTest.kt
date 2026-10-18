package com.mckimquyen.reader.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReadingStatsAssemblerTest {

    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun assemble_empty_producesZeroesAndEmptyCollections() {
        val stats = ReadingStatsAssembler.assemble(
            dayCounts = emptyList(),
            totalRead = 0,
            topFeeds = emptyList(),
            today = today,
        )
        assertEquals(0, stats.totalRead)
        assertEquals(0, stats.readToday)
        assertEquals(0, stats.activeDays)
        assertEquals(0.0, stats.avgPerActiveDay, 0.001)
        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.longestStreak)
        assertEquals(emptyMap<LocalDate, Int>(), stats.heatmap)
        assertEquals(emptyList<FeedReadCount>(), stats.topFeeds)
    }

    @Test
    fun assemble_aggregatesCountsRoundsAverageAndExtractsToday() {
        val dayCounts = listOf(
            ReadingDayCount("2026-10-16", 1),
            ReadingDayCount("2026-10-17", 2),
            ReadingDayCount("2026-10-18", 4),
        )
        val top = listOf(FeedReadCount("f1", "Feed 1", 5))

        val stats = ReadingStatsAssembler.assemble(
            dayCounts = dayCounts,
            totalRead = 12,
            topFeeds = top,
            today = today,
        )

        assertEquals(12, stats.totalRead)
        assertEquals(4, stats.readToday)
        assertEquals(3, stats.activeDays)
        // 7 bài qua 3 ngày active = 2.333... làm tròn 1 chữ số thập phân = 2.3
        assertEquals(2.3, stats.avgPerActiveDay, 0.001)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        assertEquals(top, stats.topFeeds)
        assertEquals(3, stats.heatmap.size)
        assertEquals(4, stats.heatmap[today])
    }

    @Test
    fun assemble_skipsUnparsableDayStringsWithoutThrowing() {
        val dayCounts = listOf(
            ReadingDayCount("invalid-day", 5),
            ReadingDayCount("2026-10-18", 2),
        )

        val stats = ReadingStatsAssembler.assemble(dayCounts, 2, emptyList(), today)

        assertEquals(1, stats.activeDays)
        assertEquals(2, stats.readToday)
    }
}
