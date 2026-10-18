package com.mckimquyen.reader.domain.model.statistics

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Nguồn "hôm nay" và đầu cửa sổ 365 ngày theo múi giờ local, có thể thay clock/zone khi test. */
class ReadingStatisticsCalendar(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    @Inject
    constructor() : this(Clock.systemDefaultZone(), ZoneId.systemDefault())

    fun today(): LocalDate = LocalDate.now(clock.withZone(zoneId))

    fun windowStartMillis(): Long =
        today().minusDays((WINDOW_DAYS - 1).toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()

    companion object {
        const val WINDOW_DAYS = 365
    }
}
