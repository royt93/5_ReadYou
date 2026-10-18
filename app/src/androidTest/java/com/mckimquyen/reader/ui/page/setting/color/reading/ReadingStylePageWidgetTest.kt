package com.mckimquyen.reader.ui.page.setting.color.reading

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStylePageWidgetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readingStylePage_rendersReadPositionSyncToggleProperly() {
        composeRule.setContent {
            val navController = rememberNavController()
            ReadingStylePage(navController = navController)
        }

        val expectedTitle = composeRule.activity.getString(R.string.read_position_sync)
        composeRule.onNodeWithText(expectedTitle, useUnmergedTree = true).assertIsDisplayed()
    }
}
