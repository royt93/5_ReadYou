package com.mckimquyen.reader.ui.page.setting.vip

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric: VipKeys dùng android.util.Base64 (lazy decode) nên cần runtime Android.
 * `application = Application::class` để KHÔNG boot RApp (tránh kích hoạt ad SDK / Hilt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VipKeysTest {

    @Test
    fun `key 30 ngay tra 30`() {
        assertEquals(30, VipKeys.lookupDays(VipKeys.VIP_30D_KEY))
    }

    @Test
    fun `key 3 ngay tra 3`() {
        assertEquals(3, VipKeys.lookupDays(VipKeys.VIP_3D_KEY))
    }

    @Test
    fun `key sai tra null`() {
        assertNull(VipKeys.lookupDays("WRONG-KEY-123"))
    }

    @Test
    fun `key rong tra null`() {
        assertNull(VipKeys.lookupDays(""))
        assertNull(VipKeys.lookupDays("   "))
    }

    @Test
    fun `lookup tu dong trim khoang trang`() {
        assertEquals(30, VipKeys.lookupDays("  ${VipKeys.VIP_30D_KEY}  "))
        assertEquals(3, VipKeys.lookupDays("\t${VipKeys.VIP_3D_KEY}\n"))
    }

    @Test
    fun `lookup case-sensitive`() {
        assertNull(VipKeys.lookupDays(VipKeys.VIP_30D_KEY.uppercase()))
    }

    @Test
    fun `rewarded grant 3 ngay`() {
        assertEquals(3, VipKeys.REWARDED_DAYS)
    }

    @Test
    fun `hai key khac nhau va khong rong`() {
        assertTrue(VipKeys.VIP_30D_KEY.isNotBlank())
        assertTrue(VipKeys.VIP_3D_KEY.isNotBlank())
        assertNotEquals(VipKeys.VIP_30D_KEY, VipKeys.VIP_3D_KEY)
    }
}
