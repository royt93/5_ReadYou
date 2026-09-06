package com.mckimquyen.reader.domain.model.watchdog

import androidx.annotation.Keep
import java.util.UUID

/**
 * Đại diện cho một từ khóa được người dùng cấu hình theo dõi khẩn cấp (Watchdog Keyword).
 */
@Keep
data class WatchdogKeyword(
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val matchCount: Int = 0,
)
