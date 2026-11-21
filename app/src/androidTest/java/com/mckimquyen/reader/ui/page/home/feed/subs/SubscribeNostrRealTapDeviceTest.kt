package com.mckimquyen.reader.ui.page.home.feed.subs

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
import com.mckimquyen.reader.R
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.currentAccountType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

private const val CLASS_TIMEOUT_SECONDS = 120L
private const val WAIT_MILLIS = 15_000L
private const val NOT_YET_REAL_ROWS = -1L

/**
 * The whole way a user adds a Nostr source, on the REAL dialog and the phone's REAL Room: type an npub into the real box (one
 * performTextInput, never retyped), press the real Search button, wait for the preview, press the real Subscribe button, and read the
 * stored source back from Room. The relay is a fake on 127.0.0.1, so nothing reaches the network.
 *
 * Safety: [SubscribeViewModel.subscribe] writes a source with a RANDOM id into the group the screen selected. The test forces the
 * selection to its own probe group BEFORE pressing Subscribe, so the owner's real groups are never touched; afterwards the probe group
 * is deleted (its feeds and their articles go with it by cascade) and the owner's own feed and article counts are compared with the
 * counts taken before.
 * NOT covered: the real keyboard, a swipe, MainActivity/HomeEntry, a real relay, Bluesky and web sources, the duplicate refusal, any
 * account other than Local.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeNostrRealTapDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val relay = NostrFakeRelay()
    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)
    private var shown: SubscribeViewModel? = null
    private val app: RApp get() = LocalRssSvFactory.app()
    private val npub: String = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
    private val feedUrl: String get() = NostrFeedMapper.feedUrl(npub)

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "Adding a Nostr source only runs for the Local account; this phone is on another account"
        }
        rig = BlueskyGuardRig()
        runBlocking {
            removeStoredNostrSource()
            rig.cleanUp()
            realRowsBefore = ownerRowCounts()
            rig.createGroup()
        }
    }

    /** Runs even when a test failed: the view model first, then the probe group (cascade) and any source this test added. */
    @After
    fun tearDown() {
        try {
            runBlocking {
                removeStoredNostrSource()
                rig.cleanUp()
            }
        } finally {
            relay.close()
            rig.close()
        }
    }

    /** Only a source whose group is the PROBE group is removed: a source of the owner with the same npub is never touched (and would stop the test). */
    private suspend fun removeStoredNostrSource() {
        val dao = app.androidDatabase.feedDao()
        dao.queryByLink(app.currentAccountId, feedUrl).filter { it.groupId == rig.groupId }.forEach { dao.delete(it) }
    }

    private val placeholder get() = compose.activity.getString(R.string.feed_or_site_url)
    private val searchLabel get() = compose.activity.getString(R.string.search)
    private val subscribeLabel get() = compose.activity.getString(R.string.subscribe)
    private val expectedName = NostrFeedMapper.FEED_NAME_PREFIX + NostrDeviceFixtures.PROFILE_NAME

    private fun describe(): String {
        val state = shown?.subscribeUiState?.value
        return "feed=${state?.feed != null} articles=${state?.articles?.size} locked=${state?.lockLinkInput} error=${state?.errorMessage?.length} " +
            "visible=${state?.visible} search=${state?.isSearchPage} group=${state?.selectedGroupId?.isNotEmpty()} relayRequests=${relay.requestsReceived()}"
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    private fun openDialog() {
        val viewModel = ViewModelProvider(compose.activity, subscribeViewModelFactory(relay.url))[SubscribeViewModel::class.java]
        shown = viewModel
        compose.runOnUiThread { compose.activity.setContent { SubscribeDialog(viewModel) } }
        compose.runOnUiThread { viewModel.showDrawer() }
        waitFor("the link field") { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun typeAndSearch() {
        compose.onNodeWithText(placeholder).performTextInput(npub)
        waitFor("the npub in the field") { compose.onAllNodes(hasSetTextAction() and hasText(npub)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(searchLabel).performClick()
        waitFor("the Nostr preview") { requireNotNull(shown).subscribeUiState.value.feed != null }
    }

    /** Forces the group the screen would save into; the screen's own default is the owner's FIRST real group, which must never be used here. */
    private fun selectTheProbeGroup() {
        val viewModel = requireNotNull(shown)
        compose.runOnUiThread { viewModel.selectedGroup(rig.groupId) }
        waitFor("the probe group selected") { viewModel.subscribeUiState.value.selectedGroupId == rig.groupId }
    }

    private fun scalar(sql: String): Long = app.androidDatabase.openHelper.writableDatabase.query(sql).use { c ->
        c.moveToFirst()
        c.getLong(0)
    }

    /** The owner's own rows: the probe prefix does NOT cover a source added by the tap (random id), so rows in the probe GROUP are excluded too. */
    private fun ownerRowCounts(): Pair<Long, Long> = Pair(
        scalar("SELECT COUNT(*) FROM feed WHERE id NOT LIKE '${BlueskyGuardRig.PROBE_PREFIX}%' AND groupId <> '${rig.groupId}'"),
        scalar(
            "SELECT COUNT(*) FROM article WHERE feedId NOT LIKE '${BlueskyGuardRig.PROBE_PREFIX}%' " +
                "AND feedId NOT IN (SELECT id FROM feed WHERE groupId = '${rig.groupId}')",
        ),
    )

    private fun probeGroupRows(): Long = listOf(
        "SELECT COUNT(*) FROM feed WHERE groupId = '${rig.groupId}'",
        "SELECT COUNT(*) FROM article WHERE feedId IN (SELECT id FROM feed WHERE groupId = '${rig.groupId}')",
        "SELECT COUNT(*) FROM \"group\" WHERE id = '${rig.groupId}'",
    ).sumOf { scalar(it) }

    private fun articlesOf(feedId: String): Long = scalar("SELECT COUNT(*) FROM article WHERE feedId = '$feedId'")

    /**
     * subscribe() writes the source FIRST and the notes in a SECOND statement (not one transaction). Waiting for the source row alone lets a
     * test read zero notes, or lets tearDown delete the group (cascade) while the notes are still being written, which fails with a foreign
     * key error that is reported in the NEXT test. So wait for both.
     */
    private fun waitForTheSourceAndItsNote() {
        waitFor("the source and its note to be saved") { storedProbeSource()?.let { articlesOf(it.id) >= 1L } == true }
    }

    private fun storedProbeSource(): Feed? = runBlocking {
        app.androidDatabase.feedDao().queryByLink(app.currentAccountId, feedUrl).singleOrNull { it.groupId == rig.groupId }
    }

    @Test
    fun typingAnNpubSearchingAndPressingSubscribe_savesTheNostrSourceInRoom() {
        openDialog()
        typeAndSearch()
        compose.onNodeWithText(expectedName).assertIsDisplayed()
        selectTheProbeGroup()
        assertEquals("nothing is saved before Subscribe", null, storedProbeSource())

        compose.onNodeWithText(subscribeLabel).performClick()
        waitForTheSourceAndItsNote()

        val saved = requireNotNull(storedProbeSource())
        assertEquals(Feed.NOSTR_SOURCE_TYPE, saved.sourceType)
        assertEquals("the stored address is the npub form", feedUrl, saved.url)
        assertEquals(feedUrl, saved.sourceUrl)
        assertEquals(expectedName, saved.name)
        assertEquals(rig.groupId, saved.groupId)
        assertEquals(app.currentAccountId, saved.accountId)
        assertEquals("the signed note was saved with the source", 1L, articlesOf(saved.id))
    }

    @Test
    fun theSourceAddedByTheTap_isFoundAgainByItsStoredAddress() {
        openDialog()
        typeAndSearch()
        selectTheProbeGroup()
        compose.onNodeWithText(subscribeLabel).performClick()
        waitForTheSourceAndItsNote()
        val found = runBlocking { app.androidDatabase.feedDao().queryBySourceUrl(app.currentAccountId, feedUrl) }
        assertTrue("the real index must find the source by its stored address", found.any { it.groupId == rig.groupId })
    }

    @Test
    fun pressingSearchOnly_savesNothing() {
        openDialog()
        typeAndSearch()
        compose.onNodeWithText(expectedName).assertIsDisplayed()
        assertEquals("a preview is not a subscription", null, storedProbeSource())
        assertNotNull(shown)
    }

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeft_andTheRealDataIsUntouched() {
        openDialog()
        typeAndSearch()
        selectTheProbeGroup()
        compose.onNodeWithText(subscribeLabel).performClick()
        waitForTheSourceAndItsNote()
        assertTrue("the tap wrote rows into the probe group", probeGroupRows() > 0)
        runBlocking {
            removeStoredNostrSource()
            rig.cleanUp()
        }
        assertEquals("nothing of the probe is left, group included", 0L, probeGroupRows())
        assertEquals("the owner's own feeds and articles are exactly as before", realRowsBefore, ownerRowCounts())
    }
}
