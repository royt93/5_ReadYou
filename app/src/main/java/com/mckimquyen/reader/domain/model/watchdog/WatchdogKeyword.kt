package com.mckimquyen.reader.domain.model.watchdog

import androidx.annotation.Keep
import java.util.UUID

/**
 * Đại diện cho một từ khóa được người dùng cấu hình theo dõi khẩn cấp (Watchdog Keyword).
 * Hỗ trợ snooze (tạm ngắt thông báo đến thời điểm chỉ định) và quiet hours (khung giờ yên tĩnh).
 */
@Keep
data class WatchdogKeyword(
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val matchCount: Int = 0,
    val snoozeUntil: Long? = null,
    val quietHoursStart: Int? = null, // Phút trong ngày (0..1439, ví dụ 22:00 = 1320)
    val quietHoursEnd: Int? = null,   // Phút trong ngày (0..1439, ví dụ 07:00 = 420)
)
