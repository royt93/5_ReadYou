package com.mckimquyen.reader.domain.model.rpg

data class StreakRescueOffer(
    val streakDays: Int,
    val missedDays: Int,
)

/**
 * Pure policy for prompting users about a broken reading streak.
 * Only prompts when saved streak is >= 3 days, missed days >= 2, no shield armed, and not yet prompted today (UTC).
 */
object StreakRescuePolicy {
    const val MIN_STREAK_TO_OFFER = 3
    const val MILLIS_PER_DAY = 86_400_000L
    private const val MIN_MISSED_DAYS = 2

    fun evaluate(
        streakDays: Int,
        lastReadEpochDay: Long,
        shieldActive: Boolean,
        todayEpochDay: Long,
        lastPromptEpochDay: Long,
    ): StreakRescueOffer? {
        if (streakDays < MIN_STREAK_TO_OFFER) return null
        if (shieldActive) return null
        if (lastReadEpochDay <= 0L) return null
        val gap = (todayEpochDay - lastReadEpochDay).toInt()
        if (gap < MIN_MISSED_DAYS) return null
        if (lastPromptEpochDay == todayEpochDay) return null
        return StreakRescueOffer(streakDays = streakDays, missedDays = gap)
    }

    fun epochDay(nowMillis: Long): Long = nowMillis / MILLIS_PER_DAY
}
