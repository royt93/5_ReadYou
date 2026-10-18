package com.mckimquyen.reader.ui.page.collection

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SaveSmartCollectionDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialog_rendersPreFilledQueryAndName() {
        composeTestRule.setContent {
            SaveSmartCollectionDialog(
                initialQuery = "AI models",
                initialName = "AI Models",
                onDismiss = {},
                onSave = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TEST_TAG_QUERY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TEST_TAG_SAVE).assertIsEnabled()
    }

    @Test
    fun dialog_emptyQueryOrName_disablesSaveButton() {
        composeTestRule.setContent {
            SaveSmartCollectionDialog(
                initialQuery = "",
                initialName = "",
                onDismiss = {},
                onSave = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG_SAVE).assertIsNotEnabled()
    }

    @Test
    fun dialog_clickingSave_invokesCallbackWithCleanValues() {
        var savedName = ""
        var savedQuery = ""
        composeTestRule.setContent {
            SaveSmartCollectionDialog(
                initialQuery = "renewable energy",
                initialName = "Clean Energy",
                onDismiss = {},
                onSave = { name, query ->
                    savedName = name
                    savedQuery = query
                },
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG_SAVE).performClick()

        assertEquals("Clean Energy", savedName)
        assertEquals("renewable energy", savedQuery)
    }
}
