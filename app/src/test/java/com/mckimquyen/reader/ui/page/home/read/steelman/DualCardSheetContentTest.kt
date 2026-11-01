package com.mckimquyen.reader.ui.page.home.read.steelman

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualCardSheetContentTest {
    @get:Rule val compose = createComposeRule()

    private fun ready(wasCut: Boolean = false) =
        DualCardUiState.Ready(Bitmap.createBitmap(8, 14, Bitmap.Config.ARGB_8888), wasCut)

    @Test fun ready_showsPreviewAndShare_withoutCutNote() {
        compose.setContent { DualCardSheetContent(ready(), onShare = {}, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DUAL_SHEET_SHARE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DUAL_SHEET_CUT_NOTE_TAG).assertDoesNotExist()
    }

    @Test fun ready_withCut_showsCutNote() {
        compose.setContent { DualCardSheetContent(ready(wasCut = true), onShare = {}, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_CUT_NOTE_TAG).assertIsDisplayed()
    }

    @Test fun clickingShare_invokesCallback() {
        var shares = 0
        compose.setContent { DualCardSheetContent(ready(), onShare = { shares++ }, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_SHARE_TAG).performClick()
        assertEquals(1, shares)
    }

    @Test fun rendering_disablesShare() {
        compose.setContent { DualCardSheetContent(DualCardUiState.Rendering, onShare = {}, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_SHARE_TAG).assertIsNotEnabled()
    }

    @Test fun error_showsRetry_andRetryInvokesCallback() {
        var retries = 0
        compose.setContent {
            DualCardSheetContent(DualCardUiState.Error(com.mckimquyen.reader.R.string.echo_dual_error), onShare = {}, onRetry = { retries++ })
        }
        compose.onNodeWithTag(DUAL_SHEET_ERROR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DUAL_SHEET_RETRY_TAG).performClick()
        assertEquals(1, retries)
    }
}
