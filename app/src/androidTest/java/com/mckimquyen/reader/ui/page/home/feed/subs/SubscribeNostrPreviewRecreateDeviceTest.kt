package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.UiAutomation
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrProfileName
import com.mckimquyen.reader.ui.ext.currentAccountType
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What survives, for the Nostr preview of the Add Source dialog, when its Activity is recreated or the screen is rotated.
 * The view model comes from a real ViewModelStore (ViewModelProvider on the test Activity), built from the app's real
 * services but reading a fake relay on 127.0.0.1; nothing here reaches the network.
 *
 * NOT covered: MainActivity/HomeEntry and hiltViewModel() scoping (FRONT-02d covers typed text there), the app going to the
 * background, the process being killed, a real language or dark-mode change, two relays, a real relay.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeNostrPreviewRecreateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val relay = NostrFakeRelay()
    private val automation: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var initialAutoRotate = AUTO_ROTATE_ON
    private var shown: SubscribeViewModel? = null
    private val npub: String = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))

    @Before
    fun rememberAutoRotate() {
        initialAutoRotate = Settings.System.getInt(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            AUTO_ROTATE_ON,
        )
    }

    /** Gives the phone back the rotation setting the owner had, and stops the fake relay. */
    @After
    fun cleanUp() {
        // A test that stopped while the screen was frozen sideways must be turned upright first; UNFREEZE alone
        // leaves the phone's user_rotation where the test left it.
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        if (initialAutoRotate == AUTO_ROTATE_ON) automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        relay.close()
    }

    private val placeholder get() = compose.activity.getString(R.string.feed_or_site_url)
    private val searchLabel get() = compose.activity.getString(R.string.search)
    private val expectedName = NostrFeedMapper.FEED_NAME_PREFIX + NostrDeviceFixtures.PROFILE_NAME

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    /** Counts and flags only: the screen can show the owner's own text. */
    private fun describe(): String {
        val state = shown?.subscribeUiState?.value
        return "hasViewModel=${shown != null} feed=${state?.feed != null} articles=${state?.articles?.size} " +
            "locked=${state?.lockLinkInput} error=${state?.errorMessage?.length} visible=${state?.visible} " +
            "relayRequests=${relay.requestsReceived()}"
    }

    /** The view model of [activity], taken from its ViewModelStore (the same instance across recreate). */
    private fun viewModelOf(activity: ComponentActivity): SubscribeViewModel =
        ViewModelProvider(activity, subscribeViewModelFactory(relay.url))[SubscribeViewModel::class.java]

    /** Puts the real dialog on [activity]. Unlike MainActivity, this test Activity does not rebuild its content after recreate. */
    private fun showDialogOn(activity: ComponentActivity, viewModel: SubscribeViewModel) {
        compose.runOnUiThread { activity.setContent { SubscribeDialog(viewModel) } }
    }

    private fun openDialog() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The Add Source dialog only searches Nostr for the Local account; this phone is on another account"
        }
        val viewModel = viewModelOf(compose.activity)
        shown = viewModel
        showDialogOn(compose.activity, viewModel)
        compose.runOnUiThread { viewModel.showDrawer() }
        waitFor("the link field") { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** After a recreate: same view model from the new Activity's store, dialog put back, and the preview title on screen. */
    private fun recreateAndShowAgain(): SubscribeViewModel {
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        val after = compose.activity
        assertNotSame("the activity must really have been recreated", before, after)
        val again = viewModelOf(after)
        assertSame("the ViewModelStore must hand back the same view model", shown, again)
        showDialogOn(after, again)
        waitFor("the preview title back in the dialog") {
            compose.onAllNodes(hasText(expectedName)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(expectedName).assertIsDisplayed()
        return again
    }

    private fun typeNpubAndSearch() {
        compose.onNodeWithText(placeholder).performTextInput(npub)
        waitFor("the npub in the field") { compose.onAllNodes(hasSetTextAction() and hasText(npub)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(searchLabel).performClick()
    }

    private fun waitForPreview() = waitFor("the Nostr preview") { requireNotNull(shown).subscribeUiState.value.feed != null }

    private fun assertPreviewIsComplete(viewModel: SubscribeViewModel) {
        val state = viewModel.subscribeUiState.value
        assertEquals("the preview must be named after the signed profile", expectedName, state.feed?.name)
        assertEquals("the one signed note must be in the preview", 1, state.articles.size)
        assertTrue("the box must stay locked while a preview is shown", state.lockLinkInput)
        assertEquals("a preview is not an error", "", state.errorMessage)
        assertFalse("a Nostr preview is not a web preview", state.webPreview)
    }

    @Test
    fun fixtures_areSignedAndNameTheProfile() {
        val note = requireNotNull(NostrEvent.parse(NostrDeviceFixtures.NOTE_JSON))
        val profile = requireNotNull(NostrEvent.parse(NostrDeviceFixtures.PROFILE_JSON))
        assertTrue(note.isValid(NostrDeviceFixtures.AUTHOR_HEX))
        assertTrue(profile.isValidProfile(NostrDeviceFixtures.AUTHOR_HEX))
        assertEquals(NostrDeviceFixtures.PROFILE_NAME, NostrProfileName.fromContent(profile.content))
        assertNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
    }

    @Test
    fun previewSurvivesRecreate() {
        openDialog()
        typeNpubAndSearch()
        waitForPreview()
        assertPreviewIsComplete(requireNotNull(shown))

        val again = recreateAndShowAgain()
        assertPreviewIsComplete(again)
        assertEquals("one search, one request to the fake relay", 1, relay.requestsReceived())
    }

    @Test
    fun previewSurvivesRealRotation() {
        openDialog()
        typeNpubAndSearch()
        waitForPreview()
        val viewModel = requireNotNull(shown)
        val activityBefore = compose.activity

        automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        waitFor("landscape") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertPreviewIsComplete(viewModel)
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        waitFor("portrait") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertPreviewIsComplete(viewModel)
        // The plain test Activity has no configChanges, so a rotation recreates it, unlike MainActivity (FRONT-02d).
        // Either outcome keeps the same view model; we record which one happened instead of assuming.
        val recreated = compose.activity !== activityBefore
        val again = viewModelOf(compose.activity)
        assertSame("the view model must survive the rotation (recreated=$recreated)", viewModel, again)
        assertEquals(1, relay.requestsReceived())
    }

    @Test
    fun recreateWhileTheRelayIsStillAnswering_stillShowsThePreview() {
        val gate = CompletableDeferred<Unit>()
        relay.holdAnswerUntil(gate)
        openDialog()
        typeNpubAndSearch()
        waitFor("the search to reach the relay") { relay.requestsReceived() == 1 }
        assertTrue("the box is locked while searching", requireNotNull(shown).subscribeUiState.value.lockLinkInput)
        assertEquals(null, requireNotNull(shown).subscribeUiState.value.feed)

        val before = compose.activity
        compose.activityRule.scenario.recreate()
        assertNotSame("the activity must really have been recreated", before, compose.activity)
        val again = viewModelOf(compose.activity)
        assertSame("the same view model must still be waiting for the relay", shown, again)
        showDialogOn(compose.activity, again)
        gate.complete(Unit)
        waitForPreview()
        waitFor("the preview title in the new dialog") { compose.onAllNodes(hasText(expectedName)).fetchSemanticsNodes().isNotEmpty() }
        assertPreviewIsComplete(again)
        assertEquals("recreate must not start a second search", 1, relay.requestsReceived())
    }

    private companion object {
        const val WAIT_MILLIS = 15_000L
        const val AUTO_ROTATE_ON = 1
    }
}
