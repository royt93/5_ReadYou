package com.mckimquyen.reader.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReadingStatisticsCalendarTest {

    @Test
    fun calendar_resolvesTodayAndWindowAcrossZones() {
        val instant = Instant.parse("2026-12-31T23:30:00Z")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val cal = ReadingStatisticsCalendar(Clock.fixed(instant, ZoneId.of("UTC")), tokyo)

        assertEquals(LocalDate.of(2027, 1, 1), cal.today())

        val expectedStart = LocalDate.of(2027, 1, 1)
            .minusDays((ReadingStatisticsCalendar.WINDOW_DAYS - 1).toLong())
            .atStartOfDay(tokyo)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedStart, cal.windowStartMillis())
    }
}
