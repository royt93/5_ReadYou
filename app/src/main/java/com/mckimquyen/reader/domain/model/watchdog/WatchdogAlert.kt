package com.mckimquyen.reader.domain.model.watchdog

import androidx.annotation.Keep
import java.util.UUID

/**
 * Một bản ghi lịch sử: bài viết đã kích hoạt cảnh báo Watchdog (Alert Inbox entry).
 * Được lưu lại kể cả khi từ khóa đang bị snooze/quiet-hours (chỉ thông báo bị chặn, lịch sử vẫn ghi).
 */
@Keep
data class WatchdogAlert(
    val id: String = UUID.randomUUID().toString(),
    val articleId: String,
    val articleTitle: String,
    val feedName: String,
    val keyword: String,
    val matchedExcerpt: String,
    val detectedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)
