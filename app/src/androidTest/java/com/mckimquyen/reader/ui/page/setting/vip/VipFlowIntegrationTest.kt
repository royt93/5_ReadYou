package com.mckimquyen.reader.ui.page.setting.vip

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test: kích hoạt VIP THẬT qua VIP API (không mock, deterministic).
 */
@RunWith(AndroidJUnit4::class)
class VipFlowIntegrationTest {

    @Before
    fun setUp() {
        AdManager.setConfig(
            AdSdkConfig(
                isEnableAdmob = false,
                isDebug = true,
                vipKeySecret = AdKeys.VIP_SECRET,
            )
        )
        runCatching { AdManager.clearVipByKey() }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
    }

    @Test
    fun nhapKey30Ngay_kichHoatVIP_thanhCong() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(AdManager.isVipByKeyActive())
        val days = VipKeys.lookupDays(VipKeys.VIP_30D_KEY)
        assertTrue(days == 30)
        val ok = AdManager.activateVipByKey(context, AdKeys.VIP_SECRET, days ?: 0)
        assertTrue(ok)
        assertTrue(AdManager.isVipByKeyActive())
        val remainingMs = AdManager.getVipByKeyExpiry() - System.currentTimeMillis()
        assertTrue(remainingMs in (29L * 24 * 3600_000)..(30L * 24 * 3600_000 + 60_000))
    }

    @Test
    fun nhapKeySai_baoThatBai_khongKichHoat() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(AdManager.isVipByKeyActive())
        val days = VipKeys.lookupDays("SAI-KEY-999")
        assertTrue(days == null || days == 0)
        val ok = AdManager.activateVipByKey(context, AdKeys.VIP_SECRET, days ?: 0)
        assertFalse(ok)
        assertFalse(AdManager.isVipByKeyActive())
    }
}
