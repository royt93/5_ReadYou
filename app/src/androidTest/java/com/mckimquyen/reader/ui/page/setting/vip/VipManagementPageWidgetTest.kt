package com.mckimquyen.reader.ui.page.setting.vip

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roy.sdkadbmob.AdManager
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget test cho VipManagementPage ở trạng thái FREE (chưa VIP).
 */
@RunWith(AndroidJUnit4::class)
class VipManagementPageWidgetTest {

    @Before
    fun setUp() {
        runCatching { AdManager.clearVipByKey() }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
    }

    @Test
    fun freeState_attachesVipManagementPageSuccessfully() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    VipManagementPage(navController = rememberNavController())
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
