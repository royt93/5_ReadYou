package com.mckimquyen.reader.domain.commute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CommuteSchedulePolicyTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = utc) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun constants_areTheSpecifiedOnes() {
        assertEquals(420, CommuteSchedulePolicy.DEFAULT_LISTEN_MINUTE_OF_DAY)
        assertEquals(30, CommuteSchedulePolicy.PREPARE_LEAD_MINUTES)
        assertEquals(1440, CommuteSchedulePolicy.MINUTES_PER_DAY)
    }

    @Test fun prepareMinute_isThirtyMinutesBeforeTheListenTime() {
        assertEquals(6 * 60 + 30, CommuteSchedulePolicy.prepareMinuteOfDay(7 * 60))
    }

    @Test fun prepareMinute_wrapsBackwardsAcrossMidnight() {
        assertEquals(23 * 60 + 40, CommuteSchedulePolicy.prepareMinuteOfDay(10))
        assertEquals(23 * 60 + 30, CommuteSchedulePolicy.prepareMinuteOfDay(0))
    }

    @Test fun clamp_nullAndOutOfRange_fallBackToTheDefault() {
        assertEquals(420, CommuteSchedulePolicy.clampListenMinute(null))
        assertEquals(420, CommuteSchedulePolicy.clampListenMinute(-1))
        assertEquals(420, CommuteSchedulePolicy.clampListenMinute(1440))
    }

    @Test fun clamp_validValues_areKept() {
        assertEquals(0, CommuteSchedulePolicy.clampListenMinute(0))
        assertEquals(1439, CommuteSchedulePolicy.clampListenMinute(1439))
        assertEquals(480, CommuteSchedulePolicy.clampListenMinute(480))
    }

    @Test fun delay_beforeTodaysPrepareTime_isTheGapToToday() {
        val now = at(2026, 11, 13, 5, 0)
        assertEquals(90 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, utc))
    }

    @Test fun delay_afterTodaysPrepareTime_rollsToTomorrow() {
        val now = at(2026, 11, 13, 6, 31)
        assertEquals(23 * hour + 59 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, utc))
    }

    @Test fun delay_exactlyAtThePrepareTime_isZero() {
        val now = at(2026, 11, 13, 6, 30)
        assertEquals(0L, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, utc))
    }

    @Test fun delay_listenJustAfterMidnight_preparesTheEveningBefore() {
        val now = at(2026, 11, 13, 22, 0)
        assertEquals(100 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 10, utc))
    }

    @Test fun delay_isNeverNegative_andNeverMoreThanADayPlusAnHour() {
        for (m in 0 until 1440 step 7) {
            val d = CommuteSchedulePolicy.initialDelayMillis(at(2026, 11, 13, 12, 0), m, utc)
            assertTrue("listen=$m delay=$d", d >= 0 && d <= 25 * hour)
        }
    }

    @Test fun delay_usesTheZone_notUtc() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = at(2026, 11, 13, 5, 0, tokyo)
        assertEquals(90 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, tokyo))
    }

    @Test fun delay_takesTheDateFromTheZone_notFromUtc() {
        // New York, 20:00 on Nov 12 is already Nov 13 in UTC. Listening at 00:10 prepares at 23:40 local TODAY (3h40 away),
        // not tomorrow: reading the date in UTC would push it a whole day later.
        val ny = ZoneId.of("America/New_York")
        val now = at(2026, 11, 12, 20, 0, ny)
        assertEquals(220 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 10, ny))
    }

    @Test fun delay_acrossTheSpringForwardGap_landsOnTheWallClockTime() {
        // New York, 2026-03-08: 02:00 jumps to 03:00. Listening at 07:00 prepares at 06:30 local.
        val ny = ZoneId.of("America/New_York")
        val now = at(2026, 3, 8, 0, 30, ny)
        val delay = CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, ny)
        assertEquals(at(2026, 3, 8, 6, 30, ny), now + delay)
    }

    @Test fun delay_acrossTheFallBack_landsOnTheWallClockTime() {
        // New York, 2026-11-01: 02:00 falls back to 01:00 (a 25 hour day).
        val ny = ZoneId.of("America/New_York")
        val now = at(2026, 11, 1, 0, 30, ny)
        val delay = CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, ny)
        assertEquals(at(2026, 11, 1, 6, 30, ny), now + delay)
    }
}
