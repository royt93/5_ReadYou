package com.mckimquyen.reader.ui.page.setting.vip

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdKeysTest {

    @Test
    fun `vip secret bang key 30 ngay (single-secret design)`() {
        // Lib chỉ có 1 vipKeySecret; app dùng key 30 ngày làm secret đó.
        assertEquals(VipKeys.VIP_30D_KEY, AdKeys.VIP_SECRET)
    }

    @Test
    fun `privacy policy url la https hop le`() {
        assertTrue(AdKeys.PRIVACY_POLICY_URL.startsWith("https://"))
    }
}
