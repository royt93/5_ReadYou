package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BrainRpgWeekTest {

    @Test
    fun mondayAtLocalStart_usesThisMondayAndNextMonday() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val interval = BrainRpgWeek.interval(Instant.parse("2026-10-26T01:00:00Z"), zone)
        assertEquals(Instant.parse("2026-10-25T17:00:00Z").toEpochMilli(), interval.startMillis)
        assertEquals(Instant.parse("2026-11-01T17:00:00Z").toEpochMilli(), interval.endMillis)
    }

    @Test
    fun sundayLastMillisecond_isIncludedAndNextMondayExcluded() {
        val interval = BrainRpgWeek.interval(
            Instant.parse("2026-10-25T23:59:59.999Z"), ZoneId.of("UTC"),
        )
        assertEquals(Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(), interval.endMillis)
        assertTrue(Instant.parse("2026-10-25T23:59:59.999Z").toEpochMilli() < interval.endMillis)
    }

    @Test
    fun sameInstant_usesSuppliedLocalTimezone() {
        val instant = Instant.parse("2026-10-25T23:30:00Z")
        val utc = BrainRpgWeek.interval(instant, ZoneId.of("UTC"))
        val auckland = BrainRpgWeek.interval(instant, ZoneId.of("Pacific/Auckland"))
        assertNotEquals(utc.startMillis, auckland.startMillis)
    }
}
