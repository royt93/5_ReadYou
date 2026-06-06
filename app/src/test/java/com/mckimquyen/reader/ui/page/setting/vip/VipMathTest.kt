package com.mckimquyen.reader.ui.page.setting.vip

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit test logic thuần — chạy JVM, không Android. */
class VipMathTest {

    private val day = 24L * 60L * 60L * 1000L

    // ---------- computeElapsedProgress ----------

    @Test
    fun `progress 0 khi vua kich hoat`() {
        assertEquals(0, VipMath.computeElapsedProgress(grantedAtMs = 1000L, expiresAtMs = 1000L + day, nowMs = 1000L))
    }

    @Test
    fun `progress 50 o giua chu ky`() {
        val granted = 1000L
        val expiry = granted + day
        assertEquals(50, VipMath.computeElapsedProgress(granted, expiry, granted + day / 2))
    }

    @Test
    fun `progress 100 khi het han`() {
        val granted = 1000L
        val expiry = granted + day
        assertEquals(100, VipMath.computeElapsedProgress(granted, expiry, expiry))
    }

    @Test
    fun `progress 100 khi total am (clock skew)`() {
        assertEquals(100, VipMath.computeElapsedProgress(grantedAtMs = 5000L, expiresAtMs = 1000L, nowMs = 3000L))
    }

    @Test
    fun `progress 100 khi total bang 0`() {
        assertEquals(100, VipMath.computeElapsedProgress(grantedAtMs = 1000L, expiresAtMs = 1000L, nowMs = 1000L))
    }

    @Test
    fun `progress coerce ve 0 khi now truoc granted`() {
        val granted = 10_000L
        assertEquals(0, VipMath.computeElapsedProgress(granted, granted + day, nowMs = 5_000L))
    }

    @Test
    fun `progress coerce ve 100 khi now vuot expiry`() {
        val granted = 1000L
        val expiry = granted + day
        assertEquals(100, VipMath.computeElapsedProgress(granted, expiry, expiry + day))
    }

    @Test
    fun `progress xap xi 33 sau 1 ngay tren goi 3 ngay`() {
        val granted = 0L
        val expiry = 3 * day
        assertEquals(33, VipMath.computeElapsedProgress(granted, expiry, day))
    }

    // ---------- formatCountdown ----------

    @Test
    fun `countdown zero`() {
        assertEquals("0d 00h 00m 00s", VipMath.formatCountdown(0L))
    }

    @Test
    fun `countdown am coi nhu zero`() {
        assertEquals("0d 00h 00m 00s", VipMath.formatCountdown(-5000L))
    }

    @Test
    fun `countdown padding 2 chu so`() {
        // 5h 3m 7s
        val ms = (5L * 3600 + 3L * 60 + 7L) * 1000L
        assertEquals("0d 05h 03m 07s", VipMath.formatCountdown(ms))
    }

    @Test
    fun `countdown nhieu ngay`() {
        // 12d 03h 24m 15s
        val ms = (12L * 86_400 + 3L * 3600 + 24L * 60 + 15L) * 1000L
        assertEquals("12d 03h 24m 15s", VipMath.formatCountdown(ms))
    }

    @Test
    fun `countdown bo phan le duoi 1 giay`() {
        assertEquals("0d 00h 00m 01s", VipMath.formatCountdown(1999L))
    }
}
