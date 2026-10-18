package com.mckimquyen.reader.domain.model.onthisday

import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Ngày lịch local cần so khớp: `monthDay` dạng `MM-dd`, `currentYear` 4 chữ số. */
data class OnThisDayTarget(val monthDay: String, val currentYear: String)

class OnThisDayCalendar(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    @Inject
    constructor() : this(Clock.systemDefaultZone(), ZoneId.systemDefault())

    fun currentTarget(): OnThisDayTarget {
        val now = ZonedDateTime.now(clock.withZone(zoneId))
        return OnThisDayTarget(
            monthDay = now.format(MONTH_DAY_FORMAT),
            currentYear = now.format(YEAR_FORMAT),
        )
    }

    private companion object {
        val MONTH_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
        val YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")
    }
}
