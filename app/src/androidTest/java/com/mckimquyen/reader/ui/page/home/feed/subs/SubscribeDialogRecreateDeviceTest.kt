package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.ui.ext.currentAccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What survives when the test Activity that shows the Add Source dialog is recreated while the user has typed text in it.
 * The view model comes from a real ViewModelStore (ViewModelProvider on the test Activity), built from the app's real services;
 * the dialog is the real SubscribeDialog. The text is typed ONCE through the screen (performTextInput), never retyped: when it does
 * not arrive, the failure message says whether it never entered the field or entered and then vanished (numbers only, never text).
 * Nothing is searched, saved or fetched: no network, no Room write, and no screen rotation.
 *
 * Replaces the MainActivity version of this test (FRONT-02d): MainActivity shows a full-screen ad on about a quarter of the runs on the
 * Tecno KJ7 and then Compose finds nothing to query (measured 2026-11-18: 10 of 11 red runs had an AdActivity on top, none of 29 green).
 * This test Activity lowers that (3 red runs in 40, every one with an AdActivity on top) but does NOT remove it: the app's own ad SDK (started by RApp) can still put
 * an AdActivity over the test Activity, which then loses window focus and Compose finds no hierarchy ("No compose hierarchies found").
 * NOT covered: the "+" button and hiltViewModel() of FeedsPage, a real rotation, the app going to the background, the process being
 * killed (the view model keeps nothing then, the typed text is lost), a real language or dark-mode change.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeDialogRecreateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var shown: SubscribeViewModel? = null
    private var peakEditableTextLength = NOT_TYPED_YET

    @Before
    fun requireTheLocalAccount() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The Add Source dialog only works for the Local account; this phone is on another account"
        }
    }

    private val placeholder get() = compose.activity.getString(R.string.feed_or_site_url)

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    /** Numbers and flags only: the screen can show the owner's own text. */
    private fun describe(): String = try {
        val editable = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val focused = compose.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes()
        val onScreen = editable.map { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.length }
        val state = shown?.subscribeUiState?.value
        "editableFields=${editable.size} focusedEditable=${focused.size} lengthsOnScreen=$onScreen peakOnScreen=$peakEditableTextLength " +
            "viewModelTextLength=${state?.linkContent?.length} visible=${state?.visible} locked=${state?.lockLinkInput}"
    } catch (e: Throwable) {
        "describe failed: ${e.javaClass.simpleName}"
    }

    /** The view model of [activity], taken from its ViewModelStore (the same instance across recreate). */
    private fun viewModelOf(activity: ComponentActivity): SubscribeViewModel =
        ViewModelProvider(activity, subscribeViewModelFactory(UNUSED_RELAY_URL))[SubscribeViewModel::class.java]

    /** Puts the real dialog on [activity]. Unlike MainActivity, this test Activity does not rebuild its content after a recreate. */
    private fun showDialogOn(activity: ComponentActivity, viewModel: SubscribeViewModel) {
        compose.runOnUiThread { activity.setContent { SubscribeDialog(viewModel) } }
    }

    private fun dialogTextField() = hasSetTextAction() and hasText(PROBE_TEXT)

    private fun openDialogAndTypeOnce() {
        val viewModel = viewModelOf(compose.activity)
        shown = viewModel
        showDialogOn(compose.activity, viewModel)
        compose.runOnUiThread { viewModel.showDrawer() }
        waitFor("the link field") { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
        val before = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        check(before.isEmpty()) { "The link field was not empty before typing; refusing to append the probe" }
        peakEditableTextLength = 0
        compose.onNodeWithText(placeholder).performTextInput(PROBE_TEXT)
        waitFor("the typed text in an editable field") {
            val longest = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                .maxOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.length ?: 0 } ?: 0
            if (longest > peakEditableTextLength) peakEditableTextLength = longest
            compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** One recreate: a really new Activity, the SAME view model from its store, the dialog put back, the text on screen and editable. */
    private fun recreateAndShowAgain() {
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        val after = compose.activity
        assertNotSame("the activity must really have been recreated", before, after)
        val again = viewModelOf(after)
        assertSame("the ViewModelStore must hand back the same view model", shown, again)
        assertEquals("the view model still holds the typed text", PROBE_TEXT, again.subscribeUiState.value.linkContent)
        showDialogOn(after, again)
        waitFor("the typed text back in an editable field after the recreate") {
            compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recreate_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndTypeOnce()
        recreateAndShowAgain()
        assertTrue("the dialog is still open", requireNotNull(shown).subscribeUiState.value.visible)
    }

    @Test
    fun recreateTwice_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndTypeOnce()
        recreateAndShowAgain()
        recreateAndShowAgain()
        assertTrue("the dialog is still open", requireNotNull(shown).subscribeUiState.value.visible)
        assertEquals("the field must still be editable, exactly one", 1, compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().size)
    }

    private companion object {
        const val PROBE_TEXT = "abc-rotation-probe"
        const val WAIT_MILLIS = 15_000L
        const val NOT_TYPED_YET = -1
        const val UNUSED_RELAY_URL = "ws://127.0.0.1:1"
    }
}
