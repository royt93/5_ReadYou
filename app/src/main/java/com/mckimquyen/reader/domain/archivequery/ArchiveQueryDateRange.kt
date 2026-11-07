package com.mckimquyen.reader.domain.archivequery

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ArchiveQueryDateRange {

    /** Current calendar month plus prior 11 months. */
    private const val DEFAULT_MONTHS_BACK = 11
    const val MAX_RANGE_MONTHS = 12

    /**
     * Default range: first day of (currentMonth - 11) local start-of-day → [now].
     */
    fun default(now: Instant, zone: ZoneId): ArchiveQueryRange {
        val localNow = now.atZone(zone)
        val currentMonth = YearMonth.from(localNow)
        val startMonth = currentMonth.minusMonths(DEFAULT_MONTHS_BACK.toLong())
        val startInstant = startMonth.atDay(1).atStartOfDay(zone).toInstant()
        return ArchiveQueryRange(startInclusive = startInstant, endExclusive = now)
    }

    /**
     * Validate an explicit date range.
     * [endInclusive] is the last day the user wants included; endExclusive = endInclusive + 1 day.
     */
    fun validate(
        start: LocalDate,
        endInclusive: LocalDate,
        now: Instant,
        zone: ZoneId,
    ): RangeValidation {
        if (start.isAfter(endInclusive)) return RangeValidation.Reversed

        val todayLocal = now.atZone(zone).toLocalDate()
        if (endInclusive.isAfter(todayLocal)) return RangeValidation.FutureEnd

        val startMonth = YearMonth.from(start)
        val endMonth = YearMonth.from(endInclusive)
        val monthSpan = startMonth.until(endMonth, ChronoUnit.MONTHS) + 1
        if (monthSpan > MAX_RANGE_MONTHS) return RangeValidation.TooWide

        val startInstant = start.atStartOfDay(zone).toInstant()
        // If endInclusive is today, cap at now to avoid including future instants.
        val endExclusive = if (endInclusive == todayLocal) {
            now
        } else {
            endInclusive.plusDays(1).atStartOfDay(zone).toInstant()
        }
        return RangeValidation.Valid(ArchiveQueryRange(startInstant, endExclusive))
    }
}
