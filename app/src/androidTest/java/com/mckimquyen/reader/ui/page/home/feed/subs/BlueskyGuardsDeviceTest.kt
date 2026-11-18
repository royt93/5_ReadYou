package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.sv.BlueskyDeviceFixtures
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.ext.currentAccountType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * Whether the phone's real Room and the real SubscribeViewModel/LocalRssSv do the right thing when the same Bluesky account is added
 * twice (typed as a handle, as a DID, as the old RSS form, or after a handle change) and when an old source is synced.
 * The ViewModel is driven directly (inputLink and search): nothing is typed through the screen, and MainActivity is not started.
 * A fake Bluesky server on 127.0.0.1 answers; any request for another host is refused and counted (blockedRequests, must stay 0).
 * NOT covered: the edit-address menu (FRONT-02p), the real Bluesky network, the real buttons and dialog, SyncWorker, sources that
 * already changed handle before FRONT-02q, OPML sources.
 */
@RunWith(AndroidJUnit4::class)
class BlueskyGuardsDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The Bluesky search only runs for the Local account; this phone is on another account"
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

    private val alreadySubscribed get() = compose.activity.getString(R.string.already_subscribed)

    private fun newViewModel(): SubscribeViewModel =
        ViewModelProvider(compose.activity, rig.viewModelFactory)[SubscribeViewModel::class.java]

    /** The address of a Bluesky profile page, the form in which a DID can be typed (see SocialFeedUrlResolver). */
    private fun profileLinkOf(actor: String) = "https://bsky.app/profile/$actor"

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("Never saw: $what | ${rig.profileRequests()} profile / ${rig.feedRequests()} feed / ${rig.blockedRequests()} blocked requests")
    }

    /** Types [text] by calling the ViewModel and waits until the search is over: a preview, or a message. */
    private fun search(viewModel: SubscribeViewModel, text: String): SubscribeUiState {
        viewModel.init()
        viewModel.inputLink(text)
        viewModel.search()
        waitFor("the search to finish") {
            val state = viewModel.subscribeUiState.value
            state.feed != null || state.errorMessage.isNotEmpty()
        }
        return viewModel.subscribeUiState.value
    }

    private fun assertBlockedAsDuplicate(state: SubscribeUiState) {
        assertEquals(alreadySubscribed, state.errorMessage)
        assertNull("a duplicate must not build a preview", state.feed)
        assertTrue("the box must be editable again", !state.lockLinkInput)
        assertEquals("nothing may reach a host other than 127.0.0.1", 0, rig.blockedRequests())
    }

    // ---- 02i: the same account, typed the same way it was stored, or stored in the old RSS form

    @Test
    fun sameAccountAsApiSource_isAlreadySubscribed_withoutAskingTheServer() {
        runBlocking { rig.seed("api", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = null) }
        val state = search(newViewModel(), "@$HANDLE_A")
        assertBlockedAsDuplicate(state)
        assertEquals("the duplicate is found before any call to the server", 0, rig.profileRequests())
    }

    @Test
    fun sameAccountAsOldRssSource_isAlreadySubscribed() {
        runBlocking { rig.seed("rss", url = BlueskyFeedMapper.rssUrl(HANDLE_A), sourceUrl = null, sourceType = RSS_TYPE) }
        val state = search(newViewModel(), "@$HANDLE_A")
        assertBlockedAsDuplicate(state)
        assertEquals(0, rig.profileRequests())
    }

    // ---- 02n: one account named once by handle and once by DID

    @Test
    fun typedAsDid_whenSavedAsHandle_isAlreadySubscribed() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("handle", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = null) }
        val state = search(newViewModel(), profileLinkOf(DID_A))
        assertBlockedAsDuplicate(state)
        assertEquals("the server named the other name of the account: one profile call, then the feed call", 1, rig.profileRequests())
        assertEquals(1, rig.feedRequests())
    }

    @Test
    fun typedAsHandle_whenSavedAsDid_isAlreadySubscribed() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("did", url = BlueskyFeedMapper.feedUrl(DID_A), sourceUrl = null) }
        val state = search(newViewModel(), "@$HANDLE_A")
        assertBlockedAsDuplicate(state)
        assertEquals(1, rig.profileRequests())
    }

    // ---- 02o: the DID is remembered, so a renamed handle is still recognised

    @Test
    fun newSource_isStoredWithTheDidInSourceUrl_andTheTypedNameInUrl() {
        rig.profile(DID_A, HANDLE_A)
        val state = search(newViewModel(), "@$HANDLE_A")
        val feed = requireNotNull(state.feed) { "a new account must give a preview, got: ${state.errorMessage}" }
        assertEquals(BlueskyFeedMapper.feedUrl(HANDLE_A), feed.url)
        assertEquals("the DID outlives a handle change, so it is what the sync reads", BlueskyFeedMapper.feedUrl(DID_A), feed.sourceUrl)
        assertEquals(0, rig.blockedRequests())
        assertEquals("a preview is not saved: only the probe group exists, search() added no feed row", PROBE_GROUP_ROWS, rig.probeRowCount())
    }

    @Test
    fun renamedHandle_isCaughtThroughTheStoredDid() {
        rig.profile(DID_A, HANDLE_NEW)
        runBlocking { rig.seed("renamed", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(DID_A)) }
        val state = search(newViewModel(), "@$HANDLE_NEW")
        assertBlockedAsDuplicate(state)
        assertEquals("only the DID in sourceUrl can tell: one profile call", 1, rig.profileRequests())
    }

    @Test
    fun aDifferentAccount_isNotBlocked() {
        rig.profile(DID_B, HANDLE_B)
        runBlocking { rig.seed("other", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(DID_A)) }
        val state = search(newViewModel(), "@$HANDLE_B")
        assertNotNull("another account must not be refused", state.feed)
        assertEquals("", state.errorMessage)
        assertEquals(0, rig.blockedRequests())
    }

    // ---- 02q: an old source is remembered by its DID once a sync succeeds

    @Test
    fun oldSource_isUpgradedToItsDid_whenASyncSucceeds_andKeepsItsUrlAndItsPosts() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("old", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(HANDLE_A)) }
        val before = requireNotNull(runBlocking { rig.stored("old") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNotNull("the sync must succeed", result.outcome)
        val after = requireNotNull(runBlocking { rig.stored("old") })
        assertEquals(BlueskyFeedMapper.feedUrl(DID_A), after.sourceUrl)
        assertEquals("the address the user sees never changes", BlueskyFeedMapper.feedUrl(HANDLE_A), after.url)
        assertEquals("the posts are not lost by the upgrade", 1, rig.articleCountOf("old"))
    }

    @Test
    fun aFailedSync_leavesTheSourceUrlAlone() {
        rig.failProfileWith500()
        runBlocking { rig.seed("failing", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(HANDLE_A)) }
        val before = requireNotNull(runBlocking { rig.stored("failing") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNull("a failing server must be a failed sync", result.outcome)
        assertEquals(BlueskyFeedMapper.feedUrl(HANDLE_A), requireNotNull(runBlocking { rig.stored("failing") }).sourceUrl)
        assertEquals(0, rig.articleCountOf("failing"))
    }

    @Test
    fun aSecondSync_doesNotRewriteAnUpgradedSource() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("twice", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(DID_A)) }
        val before = requireNotNull(runBlocking { rig.stored("twice") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNotNull(result.outcome)
        val after = requireNotNull(runBlocking { rig.stored("twice") })
        assertEquals(before.sourceUrl, after.sourceUrl)
        assertEquals(before.url, after.url)
    }

    @Test
    fun aSyncThatFindsNoOwnPosts_isAFailure_andLeavesTheSourceUrlAlone() {
        rig.profile(DID_A, HANDLE_A)
        rig.onlyRepostsInTheFeed()
        runBlocking { rig.seed("noposts", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(HANDLE_A)) }
        val before = requireNotNull(runBlocking { rig.stored("noposts") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNull("no own post means a failed sync, not an empty feed", result.outcome)
        assertEquals(BlueskyFeedMapper.feedUrl(HANDLE_A), requireNotNull(runBlocking { rig.stored("noposts") }).sourceUrl)
        assertEquals(0, rig.articleCountOf("noposts"))
    }

    // ---- cleanliness

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched() {
        runBlocking { rig.seed("tmp", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = null) }
        assertTrue(rig.probeRowCount() > 0)
        runBlocking { rig.cleanUp() }
        assertEquals(0L, rig.probeRowCount())
        assertEquals("the owner's own feeds and articles are exactly as before", realRowsBefore, rig.realRowCounts())
    }

    private companion object {
        const val RSS_TYPE = "RSS"

        /** The probe group created in setUp is the only probe row a test that seeds nothing may find. */
        const val PROBE_GROUP_ROWS = 1L
        const val HANDLE_A = "alice-front02r.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02r.bsky.social"
        const val HANDLE_B = "bob-front02r.bsky.social"
        const val DID_A = "did:plc:front02raaaaaaaaaaaaaaaaaa"
        const val DID_B = "did:plc:front02rbbbbbbbbbbbbbbbbbb"
    }
}
