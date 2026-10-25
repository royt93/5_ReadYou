package com.mckimquyen.reader.domain.model.rpg

data class CategoryXpTotal(
    val categoryId: String,
    val xp: Long,
)

data class WeeklyBrainWrapped(
    val weekStartMillis: Long,
    val weekEndMillis: Long,
    val weeklyXp: Long,
    val currentStreakDays: Int,
    val topCategoryId: String?,
)
