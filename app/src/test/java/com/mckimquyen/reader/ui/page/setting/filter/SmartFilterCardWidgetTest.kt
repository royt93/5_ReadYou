package com.mckimquyen.reader.ui.page.setting.filter

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.filter.FilterAction
import com.mckimquyen.reader.domain.model.filter.FilterTargetField
import com.mckimquyen.reader.domain.model.filter.SmartFilterRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmartFilterCardWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ruleCard_displaysKeywordAndDetails() {
        val rule = SmartFilterRule(
            keyword = "[Quảng cáo]",
            targetField = FilterTargetField.TITLE,
            action = FilterAction.MARK_READ,
            isEnabled = true,
        )

        composeRule.setContent {
            SmartFilterRuleCard(
                rule = rule,
                onToggle = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithText("[Quảng cáo]").assertIsDisplayed()
        composeRule.onNodeWithText("Title • Mark as Read").assertIsDisplayed()
    }
}
