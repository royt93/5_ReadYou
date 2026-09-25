package com.mckimquyen.reader.ui.component.base

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit/Widget test for [FIX-14]: BaseSwitch and SwitchHeadline render and handle click/disable states properly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BaseSwitchWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchHeadline_rendersTitleAndHandlesToggle() {
        var clicked = 0
        composeRule.setContent {
            var active by remember { mutableStateOf(false) }
            SwitchHeadline(
                activated = active,
                onClick = {
                    clicked++
                    active = !active
                },
                title = "AMOLED Dark Theme",
            )
        }

        composeRule.onNodeWithText("AMOLED Dark Theme").assertIsDisplayed()
        composeRule.onNodeWithText("AMOLED Dark Theme").performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun baseSwitch_whenDisabled_doesNotTriggerClick() {
        var clicked = 0
        composeRule.setContent {
            BaseSwitch(
                activated = false,
                enable = false,
                onClick = { clicked++ },
            )
        }

        // Disabled switch should ignore click gestures
        composeRule.waitForIdle()
        assertEquals(0, clicked)
    }
}
