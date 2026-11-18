package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.component.ChangeUrlDialog
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionUiState
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

private const val CLASS_TIMEOUT_SECONDS = 90L
private const val WAIT_MILLIS = 15_000L
private const val NOT_YET_REAL_ROWS = -1L
private const val UI_STATE_FIELD = "_feedOptionUiState"
private const val EMPTY_TEXT = ""

/**
 * Whether the REAL change-address dialog (ChangeUrlDialog), wired to the real FeedOptionViewModel the way FeedOptionDrawer wires it,
 * does the right thing on the phone: it opens with the current address, the text replaced in its field reaches the view model,
 * "Confirm" saves both columns into the phone's real Room and closes, "Cancel" closes and saves nothing, and an empty field locks "Confirm".
 * The text is replaced with ONE action (performTextReplacement), never retyped. showDrawer() is never called and MainActivity is not started.
 * NOT covered: the long press that opens the dialog, FeedOptionDrawer and hideDrawer (the second wire of onConfirm), the real keyboard,
 * the sync after the edit (FRONT-02t), web and Nostr sources, Fever and Google Reader.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(AndroidJUnit4::class)
class ChangeUrlDialogDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)
    private var shown: FeedOptionViewModel? = null

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The change-address dialog only runs for the Local account; this phone is on another account"
        }
        rig = BlueskyGuardRig()
        runBlocking {
            rig.cleanUp()
            realRowsBefore = rig.realRowCounts()
            rig.createGroup()
        }
    }

    /** Runs even when a test failed: the probe sources must never stay in the phone's database. */
    @After
    fun tearDown() {
        try {
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    private val confirmLabel get() = compose.activity.getString(R.string.confirm)
    private val cancelLabel get() = compose.activity.getString(R.string.cancel)

    /** Numbers and flags only: the screen can show the owner's own text. */
    private fun describe(): String = try {
        val state = shown?.feedOptionUiState?.value
        "editableFields=${compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size} " +
            "dialogVisible=${state?.changeUrlDialogVisible} newUrlLength=${state?.newUrl?.length}"
    } catch (e: Throwable) {
        "describe failed: ${e.javaClass.simpleName}"
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    /** The real view model, built the way FeedItemDeviceTest and FeedAddressEditDeviceTest build it: from the running app, without Hilt. */
    private fun newViewModel(): FeedOptionViewModel {
        val app = LocalRssSvFactory.app()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
        }
        return compose.runOnUiThread { ViewModelProvider(compose.activity, factory)[FeedOptionViewModel::class.java] }
    }

    /** Puts [feed] into the view model's state as the drawer would after loading it, WITHOUT calling showDrawer(). */
    private fun load(viewModel: FeedOptionViewModel, feed: Feed) {
        val field = FeedOptionViewModel::class.java.getDeclaredField(UI_STATE_FIELD).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") // the field is a MutableStateFlow<FeedOptionUiState>; a wrong type fails loudly right here
        val state = field.get(viewModel) as MutableStateFlow<FeedOptionUiState>
        state.value = state.value.copy(feed = feed, selectedGroupId = feed.groupId)
    }

    /** The real dialog, wired exactly as FeedOptionDrawer wires it, except hideDrawer (there is no drawer in this test). */
    private fun showDialogFor(viewModel: FeedOptionViewModel) {
        compose.runOnUiThread {
            compose.activity.setContent {
                val state = viewModel.feedOptionUiState.collectAsStateValue()
                ChangeUrlDialog(
                    visible = state.changeUrlDialogVisible,
                    value = state.newUrl,
                    onValueChange = { viewModel.inputNewUrl(it) },
                    onDismissRequest = { viewModel.hideFeedUrlDialog() },
                    onConfirm = { viewModel.changeFeedUrl() },
                )
            }
        }
    }

    private fun fieldText(): String? = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    private fun dialogIsGone() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()

    /** Seeds a probe source, loads it into a new view model, opens the dialog and waits for its field. */
    private fun openDialogOn(id: String, icon: String? = null, isNotification: Boolean = false, isFullContent: Boolean = false): Feed {
        runBlocking {
            rig.seed(
                id,
                url = BlueskyFeedMapper.feedUrl(HANDLE_OLD),
                sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD),
                icon = icon,
                isNotification = isNotification,
                isFullContent = isFullContent,
            )
        }
        val seeded = stored(id)
        val viewModel = newViewModel()
        shown = viewModel
        load(viewModel, seeded)
        showDialogFor(viewModel)
        viewModel.showFeedUrlDialog()
        waitFor("the address field of the dialog") { fieldText() != null }
        return seeded
    }

    /** ONE action replaces the whole text; then the view model must hold it too (the text really travelled through the dialog). */
    private fun replaceText(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        waitFor("the replaced text in the field and in the view model") {
            fieldText() == text && requireNotNull(shown).feedOptionUiState.value.newUrl == text
        }
    }

    private fun stored(id: String): Feed = requireNotNull(runBlocking { rig.stored(id) }) { "the probe source $id is not in Room" }

    // ---- the dialog as the user meets it

    @Test
    fun opening_showsTheCurrentAddressInTheField() {
        val seeded = openDialogOn("open")
        waitFor("the current address in the field") { fieldText() == seeded.url }
        assertEquals("opening the box saves nothing", seeded, stored("open"))
    }

    @Test
    fun replacingTheAddressAndConfirming_savesBothColumns_andClosesTheDialog() {
        val seeded = openDialogOn("confirm", icon = ICON, isNotification = true, isFullContent = true)
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(confirmLabel)).assertHasClickAction().assertIsEnabled().performClick()
        waitFor("the dialog to close after Confirm") { !requireNotNull(shown).feedOptionUiState.value.changeUrlDialogVisible && dialogIsGone() }
        val saved = stored("confirm")
        assertEquals("the address typed in the dialog", NEW_ADDRESS, saved.url)
        assertEquals("the address the sync reads must change too", NEW_ADDRESS, saved.sourceUrl)
        assertEquals("only the two address columns may change", seeded, saved.copy(url = seeded.url, sourceUrl = seeded.sourceUrl))
    }

    @Test
    fun cancelling_closesTheDialog_andSavesNothing() {
        val seeded = openDialogOn("cancel")
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(cancelLabel)).assertHasClickAction().performClick()
        // A correct Cancel closes the box at once and never writes; a Cancel wired to the save would close it only AFTER the write.
        waitFor("the dialog to close after Cancel") { !requireNotNull(shown).feedOptionUiState.value.changeUrlDialogVisible && dialogIsGone() }
        assertEquals("Cancel must leave the saved source exactly as it was", seeded, stored("cancel"))
        assertEquals("the unsaved address is forgotten", "", requireNotNull(shown).feedOptionUiState.value.newUrl)
    }

    @Test
    fun anEmptyField_locksTheConfirmButton_andSavesNothing() {
        val seeded = openDialogOn("empty")
        replaceText(EMPTY_TEXT)
        compose.onNode(hasText(confirmLabel)).assertIsNotEnabled()
        // A click on a locked button does nothing; Room is written asynchronously, so this part only backs up assertIsNotEnabled.
        compose.onNode(hasText(confirmLabel)).performClick()
        compose.waitForIdle()
        assertTrue("the dialog is still open", requireNotNull(shown).feedOptionUiState.value.changeUrlDialogVisible)
        assertEquals("nothing may be saved from an empty field", seeded, stored("empty"))
    }

    // ---- cleanliness

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched() {
        runBlocking { rig.seed("tmp", url = BlueskyFeedMapper.feedUrl(HANDLE_OLD), sourceUrl = null) }
        assertTrue(rig.probeRowCount() > 0)
        runBlocking { rig.cleanUp() }
        assertEquals(0L, rig.probeRowCount())
        assertEquals("the owner's own feeds and articles are exactly as before", realRowsBefore, rig.realRowCounts())
    }

    private companion object {
        const val ICON = "https://front02u.invalid/icon.png"
        const val HANDLE_OLD = "alice-front02u.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02u.bsky.social"
        const val DID_OLD = "did:plc:front02uoldoldoldoldoldo"
        val NEW_ADDRESS: String = BlueskyFeedMapper.feedUrl(HANDLE_NEW)
    }
}
