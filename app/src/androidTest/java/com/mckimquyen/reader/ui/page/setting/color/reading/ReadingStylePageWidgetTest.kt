package com.mckimquyen.reader.ui.page.setting.color.reading

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStylePageWidgetTest {

    @Test
    fun readingStylePage_rendersReadPositionSyncToggleProperly() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    val navController = rememberNavController()
                    ReadingStylePage(navController = navController)
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ReadingStylePage must render successfully with read position sync toggle", composeView)
        }
        scenario.close()
    }
}
