package com.mckimquyen.reader.ui.page.setting.backup

import android.app.Application
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val EDGE_TOLERANCE_PX = 1f

/**
 * The restore confirmation in the right-to-left languages the app ships (Arabic `ar`, Persian `fa`, Hebrew `iw`). The dialog must follow the
 * layout direction: the buttons sit on the left side in RTL (right side in LTR) and every part stays inside the dialog. A Robolectric check, not a screenshot:
 * the on-device look in `ar` was checked on the A50s, this keeps the direction from silently turning left-to-right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebDavRestoreConfirmDialogRtlTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun checkTheDialogFollowsTheDirection(expectedDirection: LayoutDirection) {
        var direction: LayoutDirection? = null
        compose.setContent {
            direction = LocalLayoutDirection.current
            WebDavRestoreConfirmDialog(onConfirm = {}, onDismiss = {})
        }
        assertEquals("layout direction of the locale", expectedDirection, direction)

        val title = compose.onNodeWithText(text(R.string.webdav_restore_confirm_title)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val message = compose.onNodeWithText(text(R.string.webdav_restore_confirm_message)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithText(text(R.string.webdav_restore_confirm_action)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        // The buttons sit at the end of the dialog: the left half in RTL, the right half in LTR (as seen on the A50s in `ar` and `kn`).
        val middle = (message.left + message.right) / 2f
        listOf("confirm" to confirm, "cancel" to cancel).forEach { (name, b) ->
            val centre = (b.left + b.right) / 2f
            if (expectedDirection == LayoutDirection.Rtl) {
                assertTrue("$name should be in the left half in RTL: centre $centre, middle $middle", centre < middle)
            } else {
                assertTrue("$name should be in the right half in LTR: centre $centre, middle $middle", centre > middle)
            }
        }
        // Nothing is pushed outside the dialog content.
        val left = minOf(title.left, message.left)
        val right = maxOf(title.right, message.right)
        listOf("confirm" to confirm, "cancel" to cancel).forEach { (name, b) ->
            assertTrue("$name starts left of the dialog content: ${b.left} < $left", b.left >= left - EDGE_TOLERANCE_PX)
            assertTrue("$name ends right of the dialog content: ${b.right} > $right", b.right <= right + EDGE_TOLERANCE_PX)
        }
    }

    @Test @Config(qualifiers = "ar")
    fun arabic_isRightToLeft() = checkTheDialogFollowsTheDirection(LayoutDirection.Rtl)

    @Test @Config(qualifiers = "fa")
    fun persian_isRightToLeft() = checkTheDialogFollowsTheDirection(LayoutDirection.Rtl)

    @Test @Config(qualifiers = "iw")
    fun hebrew_isRightToLeft() = checkTheDialogFollowsTheDirection(LayoutDirection.Rtl)

    @Test @Config(qualifiers = "en")
    fun english_staysLeftToRight() = checkTheDialogFollowsTheDirection(LayoutDirection.Ltr)
}
