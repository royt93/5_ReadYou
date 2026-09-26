package com.mckimquyen.reader.ui.component.base

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [ENH-01]: native Compose pull-to-refresh component renders children cleanly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NativeSwipeRefreshWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swipeRefresh_rendersChildContent() {
        composeRule.setContent {
            SwipeRefresh(isRefresh = false, onRefresh = {}) {
                Text(text = "Article Feed Content")
            }
        }

        composeRule.onNodeWithText("Article Feed Content").assertIsDisplayed()
    }
}
