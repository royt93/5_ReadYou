package com.mckimquyen.reader.ui.page.setting.backup

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A restore reads a file from a server the app does not control and adds its sources and groups, so it asks first. Only the confirm button
 * may start it: cancel and a tap outside must not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebDavRestoreConfirmDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private var confirmed = 0
    private var dismissed = 0

    private fun show() {
        compose.setContent { WebDavRestoreConfirmDialog(onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }
    }

    @Test
    fun theDialogExplainsWhatRestoreDoes_andOffersBothChoices() {
        show()
        compose.onNodeWithText(text(R.string.webdav_restore_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.webdav_restore_confirm_message)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.webdav_restore_confirm_action)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed()
    }

    @Test
    fun confirm_startsTheRestoreOnce() {
        show()
        compose.onNodeWithText(text(R.string.webdav_restore_confirm_action)).performClick()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun cancel_dismissesAndNeverStartsTheRestore() {
        show()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }
}
