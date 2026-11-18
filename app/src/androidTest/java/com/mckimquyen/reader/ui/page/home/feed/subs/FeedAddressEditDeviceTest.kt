package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionUiState
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

private const val CLASS_TIMEOUT_SECONDS = 90L
private const val WAIT_MILLIS = 15_000L
private const val POLL_MILLIS = 50L
private const val NOT_YET_REAL_ROWS = -1L
private const val UI_STATE_FIELD = "_feedOptionUiState"

/**
 * Whether the phone's real Room keeps the right columns when a source address is edited (FRONT-02p), and whether the next sync then
 * asks the server about the NEW address and remembers the new account's DID (FRONT-02q).
 * The real FeedOptionViewModel is driven directly (showFeedUrlDialog, inputNewUrl, changeFeedUrl): nothing is typed through the
 * screen, showDrawer() is never called (it keeps the Compose clock busy, see the 02p report) and MainActivity is not started.
 * A fake Bluesky server on 127.0.0.1 answers. NOT covered: the long press on the address row, FeedOptionDrawer, ChangeUrlDialog,
 * web and Nostr sources (JVM tests only), Fever and Google Reader, refusing a wrong or duplicate address (02p does not).
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(AndroidJUnit4::class)
class FeedAddressEditDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)

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

    /** Runs even when a test failed: the probe sources must never stay in the phone's database. */
    @After
    fun tearDown() {
        try {
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("Never saw: $what | ${rig.profileRequests()} profile / ${rig.feedRequests()} feed requests")
    }

    /** The real view model, built the way FeedItemDeviceTest builds it: from the running app, without Hilt. */
    private fun newViewModel(): FeedOptionViewModel {
        val app = LocalRssSvFactory.app()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
        }
        return compose.runOnUiThread { ViewModelProvider(compose.activity, factory)[FeedOptionViewModel::class.java] }
    }

    /**
     * Puts [feed] into the view model's state as the drawer would after loading it, WITHOUT calling showDrawer().
     * The state is written by reflection because there is no public way to load a feed without opening the drawer.
     */
    private fun open(viewModel: FeedOptionViewModel, feed: Feed) {
        val field = FeedOptionViewModel::class.java.getDeclaredField(UI_STATE_FIELD).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") // the field is a MutableStateFlow<FeedOptionUiState>; a wrong type fails loudly right here
        val state = field.get(viewModel) as MutableStateFlow<FeedOptionUiState>
        state.value = state.value.copy(feed = feed, selectedGroupId = feed.groupId)
    }

    /** What the user does: open the address box, type [address], confirm. Returns once the view model says the box closed. */
    private fun changeAddress(viewModel: FeedOptionViewModel, feed: Feed, address: String) {
        open(viewModel, feed)
        viewModel.showFeedUrlDialog()
        assertTrue("the box opens", viewModel.feedOptionUiState.value.changeUrlDialogVisible)
        assertEquals("the box opens with the address the source has now", feed.url, viewModel.feedOptionUiState.value.newUrl)
        viewModel.inputNewUrl(address)
        viewModel.changeFeedUrl()
        // changeFeedUrl() closes the box only AFTER updateFeed() returned, so a closed box means the row is written.
        waitFor("the address box to close after saving") { !viewModel.feedOptionUiState.value.changeUrlDialogVisible }
    }

    private fun stored(id: String): Feed = requireNotNull(runBlocking { rig.stored(id) }) { "the probe source $id is not in Room" }

    // ---- 02p: both columns are written; 02q: the next sync uses the new address and remembers the new account

    @Test
    fun aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress() {
        rig.profile(DID_NEW, HANDLE_NEW)
        runBlocking { rig.seed("edit", url = BlueskyFeedMapper.feedUrl(HANDLE_OLD), sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD)) }
        val newAddress = BlueskyFeedMapper.feedUrl(HANDLE_NEW)

        changeAddress(newViewModel(), stored("edit"), newAddress)

        val saved = stored("edit")
        assertEquals("the address the user typed", newAddress, saved.url)
        assertEquals("the address the sync reads must change too, or the edit does nothing", newAddress, saved.sourceUrl)

        val result = runBlocking { rig.syncOnce(saved) }
        assertNotNull("the sync of the edited source must succeed", result.outcome)
        assertEquals("the server was asked about the NEW account only", listOf(HANDLE_NEW), rig.profileActors())
        val synced = stored("edit")
        assertEquals("the address the user sees is still the one typed", newAddress, synced.url)
        assertEquals("the sync remembers the new account's DID", BlueskyFeedMapper.feedUrl(DID_NEW), synced.sourceUrl)
        assertEquals(1, rig.articleCountOf("edit"))
        assertEquals("nothing may reach a host other than 127.0.0.1", 0, rig.blockedRequests())
    }

    @Test
    fun anRssSource_changesOnlyItsUrl() {
        runBlocking { rig.seed("rss", url = RSS_OLD, sourceUrl = null, sourceType = RSS_TYPE) }

        changeAddress(newViewModel(), stored("rss"), RSS_NEW)

        val saved = stored("rss")
        assertEquals(RSS_NEW, saved.url)
        assertNull("a plain feed has no second address and must not get one", saved.sourceUrl)
        assertEquals(RSS_TYPE, saved.sourceType)
    }

    @Test
    fun everyOtherField_isKept() {
        runBlocking {
            rig.seed(
                "fields",
                url = BlueskyFeedMapper.feedUrl(HANDLE_OLD),
                sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD),
                icon = ICON,
                isNotification = true,
                isFullContent = true,
            )
        }
        val seeded = stored("fields")
        assertTrue("the probe must start with every field set, or keeping them proves nothing", seeded.isNotification && seeded.isFullContent && seeded.icon == ICON)

        changeAddress(newViewModel(), seeded, BlueskyFeedMapper.feedUrl(HANDLE_NEW))

        val saved = stored("fields")
        assertEquals("only the two address columns may change", seeded, saved.copy(url = seeded.url, sourceUrl = seeded.sourceUrl))
        assertFalse("the address really changed", saved.url == seeded.url)
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
        const val RSS_TYPE = "RSS"
        const val RSS_OLD = "https://front02t.invalid/old.xml"
        const val RSS_NEW = "https://front02t.invalid/new.xml"
        const val ICON = "https://front02t.invalid/icon.png"
        const val HANDLE_OLD = "alice-front02t.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02t.bsky.social"
        const val DID_OLD = "did:plc:front02toldoldoldoldoldo"
        const val DID_NEW = "did:plc:front02tnewnewnewnewnewn"
    }
}
