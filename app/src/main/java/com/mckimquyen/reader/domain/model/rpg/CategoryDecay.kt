package com.mckimquyen.reader.domain.model.rpg

import java.math.BigDecimal
import java.math.RoundingMode

/** Computes category XP for display only; the persisted [baseXp] is never changed. */
object CategoryDecay {
    const val DECAY_RATE_PER_WEEK = 0.9
    const val DAYS_PER_WEEK = 7L
    const val HOURS_PER_DAY = 24L
    const val MINUTES_PER_HOUR = 60L
    const val SECONDS_PER_MINUTE = 60L
    const val MILLIS_PER_SECOND = 1_000L
    const val WEEK_MILLIS = DAYS_PER_WEEK * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND

    /** Full elapsed weeks compound a 10% display reduction; negative/future deltas produce no decay. */
    fun effectiveXp(baseXp: Long, lastPracticedAtMillis: Long, nowMillis: Long): Long {
        if (baseXp <= 0L || nowMillis <= lastPracticedAtMillis) return baseXp.coerceAtLeast(0L)
        val weeks = (nowMillis - lastPracticedAtMillis) / WEEK_MILLIS
        if (weeks <= 0L) return baseXp
        // BigDecimal avoids overflow from Long.MAX_VALUE XP * a floating-point factor.
        return BigDecimal.valueOf(baseXp)
            .multiply(BigDecimal.valueOf(DECAY_RATE_PER_WEEK).pow(weeks.coerceAtMost(MAX_DECAY_WEEKS).toInt()))
            .setScale(0, RoundingMode.FLOOR)
            .toLong()
            .coerceIn(0L, baseXp)
    }

    private const val MAX_DECAY_WEEKS = 10_000L
}
