package com.mckimquyen.reader.ui.page.setting.vip

/**
 * Logic thuần (không Android) cho VIP screen — tách riêng để unit test trên JVM.
 */
internal object VipMath {

    /**
     * Progress elapsed-semantic: bar RỖNG lúc vừa kích hoạt → ĐẦY DẦN tới khi hết hạn.
     *
     * `progress = (now − granted) / (expiry − granted) × 100`, clamp [0,100].
     * `total ≤ 0` (clock skew / đã hết hạn) → 100 (render đầy).
     */
    fun computeElapsedProgress(grantedAtMs: Long, expiresAtMs: Long, nowMs: Long): Int {
        val total = expiresAtMs - grantedAtMs
        if (total <= 0L) return 100
        val elapsed = nowMs - grantedAtMs
        return ((elapsed.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    /** Format countdown `Xd HHh MMm SSs`. remainingMs âm coi như 0. */
    fun formatCountdown(remainingMs: Long): String {
        val totalSec = (if (remainingMs < 0L) 0L else remainingMs) / 1000L
        val d = totalSec / 86_400L
        val h = (totalSec % 86_400L) / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return "%dd %02dh %02dm %02ds".format(d, h, m, s)
    }
}
