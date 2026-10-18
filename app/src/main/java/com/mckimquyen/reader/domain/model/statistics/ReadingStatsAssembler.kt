package com.mckimquyen.reader.domain.model.statistics

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.round

object ReadingStatsAssembler {
    private const val AVG_SCALE = 10.0

    fun assemble(
        dayCounts: List<ReadingDayCount>,
        totalRead: Int,
        topFeeds: List<FeedReadCount>,
        today: LocalDate,
    ): ReadingStats {
        val heatmap = LinkedHashMap<LocalDate, Int>()
        for (row in dayCounts) {
            val date = try {
                LocalDate.parse(row.day)
            } catch (_: DateTimeParseException) {
                continue
            }
            heatmap[date] = (heatmap[date] ?: 0) + row.count
        }
        val streaks = ReadingStreakCalculator.compute(heatmap.keys, today)
        val windowTotal = heatmap.values.sum()
        val activeDays = heatmap.size
        val average = if (activeDays == 0) 0.0 else round(windowTotal.toDouble() / activeDays * AVG_SCALE) / AVG_SCALE
        return ReadingStats(
            totalRead = totalRead,
            readToday = heatmap[today] ?: 0,
            avgPerActiveDay = average,
            currentStreak = streaks.current,
            longestStreak = streaks.longest,
            activeDays = activeDays,
            heatmap = heatmap,
            topFeeds = topFeeds,
        )
    }
}
