package com.mckimquyen.reader.ui.page.setting.color

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.EInkModePref
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.infrastructure.pref.not
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.component.base.BaseSwitch
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
    fun settingItemUsesLocalizedLabelsAndRowClickTogglesMode() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val title = application.getString(R.string.eink_mode_title)
        val description = application.getString(R.string.eink_mode_desc)
        var mode: EInkModePref by mutableStateOf(EInkModePref.default)

        composeRule.setContent {
            CompositionLocalProvider(LocalEInkMode provides mode) {
                MaterialTheme {
                    SettingItem(
                        title = title,
                        desc = description,
                        separatedActions = true,
                        onClick = { mode = !mode },
                    ) {
                        BaseSwitch(activated = mode.value, onClick = { mode = !mode })
                    }
                }
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(description).assertIsDisplayed()
        assertEquals(EInkModePref.OFF, mode)
        composeRule.onNodeWithText(title).performClick()
        composeRule.runOnIdle { assertEquals(EInkModePref.ON, mode) }
        composeRule.onNodeWithText(title).performClick()
        composeRule.runOnIdle { assertEquals(EInkModePref.OFF, mode) }
    }
}
