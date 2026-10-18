package com.mckimquyen.reader.domain.model.statistics

import java.time.LocalDate

data class ReadingStats(
    val totalRead: Int,
    val readToday: Int,
    val avgPerActiveDay: Double,
    val currentStreak: Int,
    val longestStreak: Int,
    val activeDays: Int,
    val heatmap: Map<LocalDate, Int>,
    val topFeeds: List<FeedReadCount>,
)
