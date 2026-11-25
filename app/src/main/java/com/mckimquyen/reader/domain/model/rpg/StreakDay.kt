package com.mckimquyen.reader.domain.model.rpg

import java.time.Instant
import java.time.ZoneId

/**
 * The calendar day a reading streak counts in. It used to be `millis / 86_400_000`, which is a UTC day: at UTC+7 a read at 06:00 and the next read at 06:00 land on UTC days
 * that are not always consecutive, so a user who read three local days in a row could lose the streak. The day is now the local calendar day.
 *
 * The zone is a parameter so tests can pin it; production uses the device zone at the moment of the read.
 */
object StreakDay {
    fun of(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
}
