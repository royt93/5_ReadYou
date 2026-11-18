package com.mckimquyen.reader.ui.page.home.feed

import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionDrawer
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import com.mckimquyen.reader.ui.page.home.feed.subs.BlueskyGuardRig
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
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

/** Owns the view models of the probe and hands out the real [FeedOptionViewModel], built from the running app, without Hilt. */
private class DrawerTestOwner(app: RApp) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
    }
}

/**
 * The whole way a user edits a source address, on the REAL screen pieces: a long press on the real FeedItem opens the real
 * FeedOptionDrawer for that source, a long press on the address line opens the real change-address dialog, and Confirm saves into the
 * phone's real Room and closes both the dialog and the drawer. One FeedOptionViewModel (from a ViewModelStoreOwner) serves every
 * hiltViewModel() call, as it does in the app. Long presses are Compose touch actions on the nodes, never screen coordinates; the text
 * is replaced with ONE action and never retyped; showDrawer() is never called by the test (the long press calls it).
 * NOT covered: FeedsPage (the list of groups and sources), swiping the drawer by hand, the real keyboard, the Delete and Clear buttons,
 * moving a source to another group, the notification switch, web and Nostr sources, Fever and Google Reader.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(AndroidJUnit4::class)
class FeedDrawerLongPressDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)
    private var owner: DrawerTestOwner? = null
    private var shown: FeedOptionViewModel? = null

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The address edit only runs for the Local account; this phone is on another account"
        }
        rig = BlueskyGuardRig()
        runBlocking {
            rig.cleanUp()
            realRowsBefore = rig.realRowCounts()
            rig.createGroup()
        }
    }

    /** Runs even when a test failed: the view model first (it collects Flows in its own scope), then the probe rows. */
    @After
    fun tearDown() {
        try {
            owner?.viewModelStore?.clear()
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
        "feedLoaded=${state?.feed != null} drawerVisible=${state?.drawerState?.isVisible} dialogVisible=${state?.changeUrlDialogVisible} " +
            "newUrlLength=${state?.newUrl?.length} editableFields=${compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size}"
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

    private fun state() = requireNotNull(shown) { "the screen was not shown yet" }.feedOptionUiState.value
    private fun drawerIsVisible() = state().drawerState.isVisible
    private fun dialogIsOpen() = state().changeUrlDialogVisible

    private fun fieldText(): String? = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    private fun dialogIsGone() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()

    private fun stored(id: String): Feed = requireNotNull(runBlocking { rig.stored(id) }) { "the probe source $id is not in Room" }

    /** Seeds a probe source and puts the real drawer with the real row of that source on the screen (the drawer is still hidden). */
    private fun showTheRowOf(id: String): Feed {
        runBlocking {
            rig.seed(
                id,
                url = BlueskyFeedMapper.feedUrl(HANDLE_OLD),
                sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD),
                isNotification = true,
                isFullContent = true,
            )
        }
        val seeded = stored(id)
        val testOwner = DrawerTestOwner(LocalRssSvFactory.app())
        owner = testOwner
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testOwner) {
                AppTheme(useDarkTheme = false) {
                    FeedOptionDrawer { FeedItem(feed = seeded, isEnded = { true }, isExpanded = { true }) }
                }
            }
        }
        // The same view model hiltViewModel() hands to FeedItem and FeedOptionDrawer: same store, same default key.
        shown = compose.runOnUiThread { ViewModelProvider(testOwner)[FeedOptionViewModel::class.java] }
        return seeded
    }

    private fun longPressTheRow(seeded: Feed) {
        compose.onNode(hasText(seeded.name), useUnmergedTree = true).performTouchInput { longClick() }
    }

    private fun longPressTheAddress(seeded: Feed) {
        compose.onNode(hasText(seeded.url), useUnmergedTree = true).performTouchInput { longClick() }
    }

    /** The whole first half of the user's way: long press the row, wait for the drawer showing THIS source's address. */
    private fun openTheDrawer(seeded: Feed) {
        longPressTheRow(seeded)
        waitFor("the drawer for the source") {
            drawerIsVisible() && state().feed?.id == seeded.id &&
                compose.onAllNodes(hasText(seeded.url), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTheDialog(seeded: Feed) {
        openTheDrawer(seeded)
        longPressTheAddress(seeded)
        waitFor("the address field of the dialog") { dialogIsOpen() && fieldText() != null }
    }

    /** ONE action replaces the whole text; then the view model must hold it too (the text really travelled through the dialog). */
    private fun replaceText(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        waitFor("the replaced text in the field and in the view model") { fieldText() == text && state().newUrl == text }
    }

    private fun waitUntilBothAreClosed(what: String, drawerToo: Boolean) {
        waitFor(what) { !dialogIsOpen() && dialogIsGone() && (!drawerToo || !drawerIsVisible()) }
    }

    // ---- the long press on the row

    @Test
    fun longPressOnTheSourceRow_opensTheDrawerForThatSource() {
        val seeded = showTheRowOf("row")
        assertTrue("the drawer starts hidden", !drawerIsVisible())
        openTheDrawer(seeded)
        assertEquals("the drawer shows the source that was pressed", seeded.id, state().feed?.id)
        assertEquals("opening the drawer saves nothing", seeded, stored("row"))
    }

    // ---- the long press on the address line

    @Test
    fun longPressOnTheAddress_opensTheChangeDialogWithTheCurrentAddress() {
        val seeded = showTheRowOf("address")
        openTheDialog(seeded)
        waitFor("the current address in the field") { fieldText() == seeded.url }
        assertEquals("opening the dialog saves nothing", seeded, stored("address"))
    }

    // ---- the whole edit

    @Test
    fun editingTheAddressAndConfirming_savesBothColumns_andClosesTheDialogAndTheDrawer() {
        val seeded = showTheRowOf("confirm")
        openTheDialog(seeded)
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(confirmLabel)).assertHasClickAction().assertIsEnabled().performClick()
        waitUntilBothAreClosed("the dialog and the drawer to close after Confirm", drawerToo = true)
        val saved = stored("confirm")
        assertEquals("the address typed in the dialog", NEW_ADDRESS, saved.url)
        assertEquals("the address the sync reads must change too", NEW_ADDRESS, saved.sourceUrl)
        assertEquals("only the two address columns may change", seeded, saved.copy(url = seeded.url, sourceUrl = seeded.sourceUrl))
    }

    @Test
    fun cancellingTheEdit_savesNothing() {
        val seeded = showTheRowOf("cancel")
        openTheDialog(seeded)
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(cancelLabel)).assertHasClickAction().performClick()
        waitUntilBothAreClosed("the dialog to close after Cancel", drawerToo = false)
        assertEquals("Cancel must leave the saved source exactly as it was", seeded, stored("cancel"))
        assertEquals("the unsaved address is forgotten", "", state().newUrl)
        assertTrue("Cancel closes only the dialog: the drawer stays open", drawerIsVisible())
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
        const val HANDLE_OLD = "alice-front02v.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02v.bsky.social"
        const val DID_OLD = "did:plc:front02voldoldoldoldoldo"
        val NEW_ADDRESS: String = BlueskyFeedMapper.feedUrl(HANDLE_NEW)
    }
}
