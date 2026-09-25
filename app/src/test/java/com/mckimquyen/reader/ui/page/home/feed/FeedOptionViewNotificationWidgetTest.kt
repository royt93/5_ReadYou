package com.mckimquyen.reader.ui.page.home.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: Espresso 3.6.1 (used by the Compose test rule) cannot drive input on Android 17 devices.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedOptionViewNotificationWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val allowNotification =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.allow_notification)

    @Test
    fun enablingNotification_requestsPermission_disablingDoesNot() {
        var permissionRequests = 0
        var toggles = 0
        composeRule.setContent {
            var enabled by remember { mutableStateOf(false) }
            FeedOptionView(
                selectedAllowNotificationPreset = enabled,
                showGroup = false,
                allowNotificationPresetOnClick = {
                    toggles++
                    enabled = !enabled
                },
                requestNotificationPermission = { permissionRequests++ },
            )
        }

        // OFF -> ON: permission is requested and the setting still toggles.
        composeRule.onAllNodesWithText(allowNotification).onFirst().performClick()
        composeRule.waitForIdle()
        assertEquals(1, permissionRequests)
        assertEquals(1, toggles)

        // ON -> OFF: no permission prompt.
        composeRule.onAllNodesWithText(allowNotification).onFirst().performClick()
        composeRule.waitForIdle()
        assertEquals(1, permissionRequests)
        assertEquals(2, toggles)
    }
}
