package com.mckimquyen.reader.ui.page.setting.vip

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import com.roy.sdkadbmob.AdManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Widget test cho VipManagementPage ở trạng thái FREE (chưa VIP).
 *
 * Free state: AdManager chưa init → isVipByKeyActive()=false, không có clock loop chạy ngầm
 * → render an toàn, không cần mock. Yêu cầu emulator/thiết bị để chạy.
 *
 * Lưu ý: assert theo string tiếng Anh (values/ default). Chạy trên thiết bị locale en.
 */
@RunWith(AndroidJUnit4::class)
class VipManagementPageWidgetTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        // đảm bảo về free state
        runCatching { AdManager.clearVipByKey() }
        rule.setContent {
            VipManagementPage(navController = rememberNavController())
        }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
    }

    @Test
    fun freeState_hienHeroFreeUser() {
        rule.onNodeWithText("Free user").assertIsDisplayed()
    }

    @Test
    fun freeState_hienNutWatchAdVaLockedButtons() {
        rule.onNodeWithText("Watch ad → 3 days VIP").assertIsDisplayed()
        rule.onNodeWithText("Buy 30-day VIP — coming soon").assertIsDisplayed()
        rule.onNodeWithText("Restore purchase — coming soon").assertIsDisplayed()
    }

    @Test
    fun redeemButton_disabledKhiInputRong_enabledKhiCoText() {
        rule.onNodeWithText("Activate").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextInput("ABC")
        rule.onNodeWithText("Activate").assertIsEnabled()
    }
}
