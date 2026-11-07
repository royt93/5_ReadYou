package com.mckimquyen.reader.domain.archivequery

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ArchiveQueryDateRangeTest {

    private val utc = ZoneId.of("UTC")
    private val saigon = ZoneId.of("Asia/Ho_Chi_Minh") // UTC+7

    // ── default range ──

    @Test
    fun `default range covers current month plus 11 prior months`() {
        // 2026-11-07T10:00:00Z → start = 2025-12-01T00:00 UTC, end = query instant
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val range = ArchiveQueryDateRange.default(now, utc)
        assertEquals(Instant.parse("2025-12-01T00:00:00Z"), range.startInclusive)
        assertEquals(now, range.endExclusive)
    }

    @Test
    fun `default range respects timezone at month boundary`() {
        // 2026-01-01T06:00:00Z = 2026-01-01T13:00 Saigon → start = 2025-02-01T00:00 Saigon
        val now = Instant.parse("2026-01-01T06:00:00Z")
        val range = ArchiveQueryDateRange.default(now, saigon)
        val expectedStart = LocalDate.of(2025, 2, 1).atStartOfDay(saigon).toInstant()
        assertEquals(expectedStart, range.startInclusive)
        assertEquals(now, range.endExclusive)
    }

    @Test
    fun `default range December to January year crossing`() {
        // In December 2026 UTC → start = January 2026
        val now = Instant.parse("2026-12-15T12:00:00Z")
        val range = ArchiveQueryDateRange.default(now, utc)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), range.startInclusive)
    }

    @Test
    fun `default range leap year February`() {
        // 2028 is a leap year; query on 2028-02-29 UTC
        val now = LocalDate.of(2028, 2, 29).atStartOfDay(utc).toInstant().plusSeconds(3600)
        val range = ArchiveQueryDateRange.default(now, utc)
        assertEquals(Instant.parse("2027-03-01T00:00:00Z"), range.startInclusive)
    }

    // ── validate ──

    @Test
    fun `validate valid 3-month range ending today caps at now`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2026, 9, 1),
            endInclusive = LocalDate.of(2026, 11, 7),
            now = now,
            zone = utc,
        )
        assertTrue(result is RangeValidation.Valid)
        val valid = result as RangeValidation.Valid
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), valid.range.startInclusive)
        // endInclusive == today → capped at now, not tomorrow
        assertEquals(now, valid.range.endExclusive)
    }

    @Test
    fun `validate past range uses day-after for endExclusive`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2026, 6, 1),
            endInclusive = LocalDate.of(2026, 10, 31),
            now = now,
            zone = utc,
        )
        assertTrue(result is RangeValidation.Valid)
        val valid = result as RangeValidation.Valid
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), valid.range.endExclusive)
    }

    @Test
    fun `validate rejects reversed dates`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2026, 11, 1),
            endInclusive = LocalDate.of(2026, 9, 1),
            now = now,
            zone = utc,
        )
        assertEquals(RangeValidation.Reversed, result)
    }

    @Test
    fun `validate today ends at captured now, not tomorrow`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2026, 11, 7),
            endInclusive = LocalDate.of(2026, 11, 7),
            now = now,
            zone = utc,
        ) as RangeValidation.Valid
        assertEquals(now, result.range.endExclusive)
    }

    @Test
    fun `validate rejects future end date`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2026, 1, 1),
            endInclusive = LocalDate.of(2027, 6, 1),
            now = now,
            zone = utc,
        )
        assertEquals(RangeValidation.FutureEnd, result)
    }

    @Test
    fun `validate rejects range exceeding 12 months`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2025, 1, 1),
            endInclusive = LocalDate.of(2026, 11, 7),
            now = now,
            zone = utc,
        )
        assertEquals(RangeValidation.TooWide, result)
    }

    @Test
    fun `validate same day range is valid`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2026, 11, 7),
            endInclusive = LocalDate.of(2026, 11, 7),
            now = now,
            zone = utc,
        )
        assertTrue(result is RangeValidation.Valid)
    }

    @Test
    fun `validate exactly 12 months is valid`() {
        val now = Instant.parse("2026-11-30T23:59:59Z")
        val result = ArchiveQueryDateRange.validate(
            start = LocalDate.of(2025, 12, 1),
            endInclusive = LocalDate.of(2026, 11, 30),
            now = now,
            zone = utc,
        )
        assertTrue(result is RangeValidation.Valid)
    }
}
