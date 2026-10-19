package com.mckimquyen.reader.ui.page.home.read.quote

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuoteCardSheetContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ready(template: QuoteCardTemplate = QuoteCardTemplate.MINIMALIST, wasCut: Boolean = false) =
        QuoteCardUiState.Ready(Bitmap.createBitmap(8, 10, Bitmap.Config.ARGB_8888), template, wasCut)

    @Test
    fun ready_showsPreviewThreeTemplatesAndShare_withoutCutNote() {
        composeTestRule.setContent {
            QuoteCardSheetContent(ready(), QuoteCardTemplate.MINIMALIST, {}, {}, {})
        }

        composeTestRule.onNodeWithTag(QUOTE_SHEET_PREVIEW_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Minimalist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gradient").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark Studio").assertIsDisplayed()
        composeTestRule.onNodeWithTag(QUOTE_SHEET_SHARE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(QUOTE_SHEET_CUT_NOTE_TAG).assertDoesNotExist()
    }

    @Test
    fun ready_withCut_showsCutNote() {
        composeTestRule.setContent {
            QuoteCardSheetContent(ready(wasCut = true), QuoteCardTemplate.MINIMALIST, {}, {}, {})
        }

        composeTestRule.onNodeWithTag(QUOTE_SHEET_CUT_NOTE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Quote was shortened").assertIsDisplayed()
    }

    @Test
    fun clickingTemplateAndShare_invokesCallbacks() {
        val selected = mutableListOf<QuoteCardTemplate>()
        var shares = 0
        composeTestRule.setContent {
            QuoteCardSheetContent(ready(), QuoteCardTemplate.MINIMALIST, { selected += it }, { shares++ }, {})
        }

        composeTestRule.onNodeWithTag(QUOTE_SHEET_TEMPLATE_TAG_PREFIX + QuoteCardTemplate.DARK_STUDIO.storageName).performClick()
        composeTestRule.onNodeWithTag(QUOTE_SHEET_SHARE_TAG).performClick()

        assertEquals(listOf(QuoteCardTemplate.DARK_STUDIO), selected)
        assertEquals(1, shares)
    }

    @Test
    fun selectedTemplateChip_exposesSelectedSemantics() {
        composeTestRule.setContent {
            QuoteCardSheetContent(ready(QuoteCardTemplate.GRADIENT_MATERIAL), QuoteCardTemplate.GRADIENT_MATERIAL, {}, {}, {})
        }

        val gradient = composeTestRule.onNodeWithTag(QUOTE_SHEET_TEMPLATE_TAG_PREFIX + "gradient").fetchSemanticsNode()
        val minimalist = composeTestRule.onNodeWithTag(QUOTE_SHEET_TEMPLATE_TAG_PREFIX + "minimalist").fetchSemanticsNode()
        assertTrue(gradient.config.getOrNull(SemanticsProperties.Selected) == true)
        assertTrue(minimalist.config.getOrNull(SemanticsProperties.Selected) != true)
    }

    @Test
    fun rendering_disablesShare() {
        composeTestRule.setContent {
            QuoteCardSheetContent(QuoteCardUiState.Rendering, QuoteCardTemplate.MINIMALIST, {}, {}, {})
        }

        composeTestRule.onNodeWithTag(QUOTE_SHEET_SHARE_TAG).assertIsNotEnabled()
    }

    @Test
    fun error_showsMessageAndRetry() {
        var retries = 0
        composeTestRule.setContent {
            QuoteCardSheetContent(
                QuoteCardUiState.Error(com.mckimquyen.reader.R.string.quote_card_error),
                QuoteCardTemplate.MINIMALIST, {}, {}, { retries++ },
            )
        }

        composeTestRule.onNodeWithTag(QUOTE_SHEET_ERROR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Could not create the quote card. Tap to retry.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
