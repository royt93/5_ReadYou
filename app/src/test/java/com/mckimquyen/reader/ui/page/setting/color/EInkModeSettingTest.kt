package com.mckimquyen.reader.ui.page.setting.color

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.EInkModePref
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.infrastructure.pref.not
import com.mckimquyen.reader.ui.component.base.BaseSwitch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EInkModeSettingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toggle_startsOffAndClickEnablesMode() {
        var mode: EInkModePref = EInkModePref.default
        val title = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.eink_mode_title)
        val description = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.eink_mode_desc)

        composeRule.setContent {
            CompositionLocalProvider(LocalEInkMode provides mode) {
                MaterialTheme {
                    Text(title)
                    Text(description)
                    BaseSwitch(activated = mode.value, onClick = { mode = !mode })
                }
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(description).assertIsDisplayed()
        assertEquals(EInkModePref.OFF, mode)
        composeRule.onNodeWithText(title).performClick()
        composeRule.runOnIdle { assertEquals(EInkModePref.ON, mode) }
    }
}
