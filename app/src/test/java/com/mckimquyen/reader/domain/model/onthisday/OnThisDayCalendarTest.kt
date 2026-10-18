package com.mckimquyen.reader.domain.model.onthisday

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class OnThisDayCalendarTest {

    private fun calendarAt(instant: String, zone: String) =
        OnThisDayCalendar(Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")), ZoneId.of(zone))

    @Test
    fun currentTarget_zeroPadsMonthAndDay() {
        val target = calendarAt("2026-02-05T12:00:00Z", "UTC").currentTarget()
        assertEquals("02-05", target.monthDay)
        assertEquals("2026", target.currentYear)
    }

    @Test
    fun currentTarget_keepsLeapDay() {
        val target = calendarAt("2028-02-29T12:00:00Z", "UTC").currentTarget()
        assertEquals("02-29", target.monthDay)
        assertEquals("2028", target.currentYear)
    }

    @Test
    fun currentTarget_usesZoneAcrossMidnight() {
        val instant = "2026-12-31T23:30:00Z"
        // UTC còn 31/12, Tokyo (UTC+9) đã sang 01/01 năm sau.
        assertEquals("12-31", calendarAt(instant, "UTC").currentTarget().monthDay)
        val tokyo = calendarAt(instant, "Asia/Tokyo").currentTarget()
        assertEquals("01-01", tokyo.monthDay)
        assertEquals("2027", tokyo.currentYear)
        // Los Angeles (UTC-8) vẫn là 31/12 buổi chiều.
        assertEquals("12-31", calendarAt(instant, "America/Los_Angeles").currentTarget().monthDay)
    }
}
