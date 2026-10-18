package com.mckimquyen.reader.domain.model.statistics

import java.time.LocalDate

data class Streaks(val current: Int, val longest: Int)

object ReadingStreakCalculator {

    /**
     * `current`: chuỗi ngày liên tiếp kết thúc hôm nay; nếu hôm nay chưa đọc nhưng hôm qua có đọc thì
     * chuỗi kết thúc hôm qua vẫn được giữ (người dùng còn đến hết hôm nay để nối tiếp).
     * `longest`: chuỗi liên tiếp dài nhất trong tập ngày. Ngày sau [today] bị bỏ qua.
     */
    fun compute(days: Set<LocalDate>, today: LocalDate): Streaks {
        val valid = days.filterNot { it.isAfter(today) }.toSortedSet()
        if (valid.isEmpty()) return Streaks(0, 0)

        var longest = 1
        var run = 1
        var previous: LocalDate? = null
        for (day in valid) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            if (run > longest) longest = run
            previous = day
        }

        val anchor = when {
            today in valid -> today
            today.minusDays(1) in valid -> today.minusDays(1)
            else -> null
        }
        var current = 0
        var cursor = anchor
        while (cursor != null && cursor in valid) {
            current++
            cursor = cursor.minusDays(1)
        }
        return Streaks(current, longest)
    }
}
