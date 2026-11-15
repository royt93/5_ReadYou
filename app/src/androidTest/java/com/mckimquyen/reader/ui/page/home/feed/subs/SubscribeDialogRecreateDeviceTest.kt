package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.UiAutomation
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.android.MainActivity
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What survives when the real MainActivity is recreated or the screen is rotated while the Add Source dialog is open
 * with text typed in it. Opens the dialog by finding the "+" button with Compose semantics (no screen coordinates),
 * types a probe string that is not a key or an address, never presses Search, never touches the network.
 *
 * NOT covered: the Nostr preview after a rotation (needs a relay), the app going to the background, the process being
 * killed (the view model keeps nothing then, the typed text is lost), a real language or dark-mode change.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeDialogRecreateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val automation: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var initialAutoRotate = AUTO_ROTATE_ON
    private var peakEditableTextLength = -1

    @Before
    fun rememberAutoRotate() {
        initialAutoRotate = Settings.System.getInt(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            AUTO_ROTATE_ON,
        )
    }

    /** Gives the phone back the rotation setting the owner had, not the one `setRotation` leaves behind. */
    @After
    fun restoreRotation() {
        automation.setRotation(
            if (initialAutoRotate == AUTO_ROTATE_ON) UiAutomation.ROTATION_UNFREEZE else UiAutomation.ROTATION_FREEZE_0,
        )
    }

    private val subscribeLabel get() = compose.activity.getString(R.string.subscribe)
    private val placeholder get() = compose.activity.getString(R.string.feed_or_site_url)

    /**
     * FeedsPage runs an infinite rotation animation (rememberInfiniteTransition), so Compose is never idle and the
     * default auto-advancing clock makes every semantics query time out. We stop the auto clock and push one frame per poll.
     */
    @Before
    fun stopTheAutoClock() {
        compose.mainClock.autoAdvance = false
    }

    private fun waitFor(what: String, timeoutMillis: Long = WAIT_MILLIS, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeByFrame()
            val met = try {
                condition()
            } catch (e: Throwable) {
                last = e
                false
            }
            if (met) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "Never saw: $what (first launch screen, a non-Local account, or an ad on top?) | ${describeScreen()}",
            last,
        )
    }

    /** Counts and flags only, never arbitrary on-screen text: the screen can show the owner's own feed titles. */
    private fun describeScreen(): String = try {
        val editable = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val withProbe = compose.onAllNodes(hasText(PROBE_TEXT)).fetchSemanticsNodes()
        val withPlaceholder = compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes()
        val plus = compose.onAllNodes(hasContentDescription(subscribeLabel) and hasClickAction()).fetchSemanticsNodes()
        val focused = compose.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes()
        val textLengths = editable.map { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.length }
        "editableFields=${editable.size} nodesWithProbeText=${withProbe.size} nodesWithPlaceholder=${withPlaceholder.size} " +
            "plusButtons=${plus.size} focusedEditable=${focused.size} editableTextLengths=$textLengths peakEditableTextLength=$peakEditableTextLength orientation=${compose.activity.resources.configuration.orientation} " +
            "activityId=${System.identityHashCode(compose.activity)}"
    } catch (e: Throwable) {
        "describeScreen failed: ${e.javaClass.simpleName}"
    }

    private fun dialogTextField() = hasSetTextAction() and hasText(PROBE_TEXT)

    private fun openDialogAndType() {
        val plusButton = hasContentDescription(subscribeLabel) and hasClickAction()
        waitFor("the + button") { compose.onAllNodes(plusButton).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(plusButton).assertCountEquals(1)
        compose.onNode(plusButton).performClick()
        waitFor("the link field") { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
        repeat(MAX_TYPING_ATTEMPTS) { attempt ->
            val fieldText = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
            if (fieldText.isNotEmpty()) {
                throw AssertionError("The link field was not empty before typing; refusing to append the probe")
            }
            peakEditableTextLength = 0
            compose.onNodeWithText(placeholder).performTextInput(PROBE_TEXT)
            try {
                waitFor("the typed text in an editable field", TYPING_WAIT_MILLIS) {
                    // Retrying is allowed only when the probe was never observed in the field.
                    val longest = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                        .maxOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.length ?: 0 } ?: 0
                    if (longest > peakEditableTextLength) peakEditableTextLength = longest
                    compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty()
                }
                return
            } catch (e: AssertionError) {
                if (peakEditableTextLength > 0 || attempt == MAX_TYPING_ATTEMPTS - 1) throw e
            }
        }
        throw AssertionError("Could not type the probe after $MAX_TYPING_ATTEMPTS attempts")
    }

    /** Open dialog + the same text + an editable (not locked) field: `readOnly` would remove the set-text action. */
    private fun assertDialogStillOpenWithText() {
        waitFor("the dialog with the typed text after the interruption") {
            compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recreate_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndType()
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        assertNotSame("the activity must really have been recreated", before, compose.activity)
        assertDialogStillOpenWithText()
    }

    @Test
    fun realRotation_keepsTheDialogOpenAndTheTypedText_withoutRecreatingTheActivity() {
        openDialogAndType()
        val before = compose.activity
        automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        waitFor("landscape") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertDialogStillOpenWithText()
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        waitFor("portrait") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertDialogStillOpenWithText()
        // MainActivity declares configChanges for orientation/screenSize, so a rotation must not recreate it.
        // If someone removes that, this line fails on purpose and the test needs a deliberate review.
        assertSame("rotation recreated the activity: configChanges changed?", before, compose.activity)
    }

    @Test
    fun recreateTwice_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndType()
        val first = compose.activity
        compose.activityRule.scenario.recreate()
        val second = compose.activity
        assertNotSame(first, second)
        assertDialogStillOpenWithText()
        compose.activityRule.scenario.recreate()
        assertNotSame(second, compose.activity)
        assertDialogStillOpenWithText()
        assertTrue("the field must still be editable", compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().size == 1)
    }

    private companion object {
        const val PROBE_TEXT = "abc-rotation-probe"
        const val WAIT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
        const val TYPING_WAIT_MILLIS = 3_000L
        const val MAX_TYPING_ATTEMPTS = 3
        const val AUTO_ROTATE_ON = 1
    }
}
