package com.mckimquyen.reader.ui.page.setting.vip

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Integration test: kích hoạt VIP THẬT qua UI → AdManager (không mock, deterministic).
 *
 * VIP-by-key chỉ đụng SharedPreferences (không cần network/Play Services), nên test này
 * exercise đầy đủ: UI nhập key → VipKeys.lookupDays → AdManager.activateVipByKey →
 * isVipByKeyActive → dialog success. Yêu cầu emulator/thiết bị để chạy.
 */
@RunWith(AndroidJUnit4::class)
class VipFlowIntegrationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        // setConfig đủ để activateVipByKey validate vipKeySecret (KHÔNG gọi initialize → không network).
        AdManager.setConfig(
            AdSdkConfig(
                isEnableAdmob = false,
                isDebug = true,
                vipKeySecret = AdKeys.VIP_SECRET,
            )
        )
        runCatching { AdManager.clearVipByKey() }
        // tránh clock loop auto-advance vô hạn khi VIP active sau khi kích hoạt
        rule.mainClock.autoAdvance = false
        rule.setContent {
            VipManagementPage(navController = rememberNavController())
        }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
    }

    @Test
    fun nhapKey30Ngay_kichHoatVIP_thanhCong() {
        // nhập đúng key 30 ngày
        rule.onNode(hasSetTextAction()).performTextInput(VipKeys.VIP_30D_KEY)
        rule.onNodeWithText("Activate").performClick()
        rule.waitForIdle()

        // AdManager đã set VIP active
        assertTrue(AdManager.isVipByKeyActive())
        val remainingMs = AdManager.getVipByKeyExpiry() - System.currentTimeMillis()
        // ~30 ngày (cho dung sai vài phút)
        assertTrue(remainingMs in (29L * 24 * 3600_000)..(30L * 24 * 3600_000 + 60_000))

        // dialog success hiển thị
        rule.onNodeWithText("Success").assertIsDisplayed()
    }

    @Test
    fun nhapKeySai_baoThatBai_khongKichHoat() {
        rule.onNode(hasSetTextAction()).performTextInput("SAI-KEY-999")
        rule.onNodeWithText("Activate").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Failed").assertIsDisplayed()
        assertTrue(!AdManager.isVipByKeyActive())
    }
}
