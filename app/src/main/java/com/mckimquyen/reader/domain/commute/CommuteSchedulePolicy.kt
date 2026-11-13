package com.mckimquyen.reader.domain.commute

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the morning episode is prepared (STRAT-01). The user picks the time they want to LISTEN; the app prepares it a
 * fixed lead earlier. Pure: no Android, so every branch is testable.
 */
object CommuteSchedulePolicy {
    const val DEFAULT_LISTEN_MINUTE_OF_DAY = 420
    const val PREPARE_LEAD_MINUTES = 30
    const val MINUTES_PER_DAY = 1440
    private const val MINUTES_PER_HOUR = 60

    /** DataStore can hold anything; a bad or missing value must never break scheduling. */
    fun clampListenMinute(raw: Int?): Int =
        if (raw == null || raw !in 0 until MINUTES_PER_DAY) DEFAULT_LISTEN_MINUTE_OF_DAY else raw

    fun prepareMinuteOfDay(listenMinuteOfDay: Int): Int =
        Math.floorMod(listenMinuteOfDay - PREPARE_LEAD_MINUTES, MINUTES_PER_DAY)

    /** Milliseconds from [nowMillis] to the next prepare time on the wall clock of [zone]; never negative. */
    fun initialDelayMillis(nowMillis: Long, listenMinuteOfDay: Int, zone: ZoneId): Long {
        val prepare = prepareMinuteOfDay(clampListenMinute(listenMinuteOfDay))
        val time = LocalTime.of(prepare / MINUTES_PER_HOUR, prepare % MINUTES_PER_HOUR)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var target = now.toLocalDate().atTime(time).atZone(zone)
        if (target.toInstant().toEpochMilli() < nowMillis) target = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return target.toInstant().toEpochMilli() - nowMillis
    }
}
