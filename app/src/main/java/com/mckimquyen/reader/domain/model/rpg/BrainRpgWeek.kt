package com.mckimquyen.reader.domain.model.rpg

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeekInterval(val startMillis: Long, val endMillis: Long)

object BrainRpgWeek {
    fun interval(now: Instant, zone: ZoneId): WeekInterval {
        val date = now.atZone(zone).toLocalDate()
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return WeekInterval(
            monday.atStartOfDay(zone).toInstant().toEpochMilli(),
            monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}
