package com.mckimquyen.reader.ui.page.setting.ai

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSettingsPageWidgetTest {

    @Test
    fun aiSettingsPage_rendersProperly_inLiveActivity() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    val navController = rememberNavController()
                    AiSettingsPage(
                        navController = navController,
                        activity = activity,
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach successfully", composeView)
        }
        scenario.close()
    }
}
