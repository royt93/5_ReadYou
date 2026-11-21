package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

private const val CLASS_TIMEOUT_SECONDS = 90L
private const val WAIT_MILLIS = 15_000L

/**
 * Typing an npub that is ALREADY stored as a source and pressing the real Search button: the dialog must say "already subscribed" and must
 * not ask the relay or show a preview. The stored source is a probe row in the probe group; cleanUp removes it with the group.
 * NOT covered: the Subscribe button (nothing to subscribe), a source of the owner with the same npub (it would give the same answer).
 */
@RunWith(AndroidJUnit4::class)
class SubscribeNostrDuplicateDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val relay = NostrFakeRelay()
    private lateinit var rig: BlueskyGuardRig
    private val app: RApp get() = LocalRssSvFactory.app()
    private val npub: String = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
    private val feedUrl: String get() = NostrFeedMapper.feedUrl(npub)
    private var shown: SubscribeViewModel? = null

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) { "Local account only" }
        rig = BlueskyGuardRig()
        runBlocking {
            rig.cleanUp()
            rig.createGroup()
            app.androidDatabase.feedDao().insert(
                Feed(
                    id = "${BlueskyGuardRig.PROBE_PREFIX}nostr_dup", name = "Nostr probe", url = feedUrl, sourceUrl = feedUrl,
                    groupId = rig.groupId, accountId = app.currentAccountId, sourceType = Feed.NOSTR_SOURCE_TYPE,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        try {
            runBlocking { rig.cleanUp() }
        } finally {
            relay.close()
            rig.close()
        }
    }

    @Test
    fun anNpubThatIsAlreadyStored_isRefused_withoutAskingTheRelay() {
        val viewModel = ViewModelProvider(compose.activity, subscribeViewModelFactory(relay.url))[SubscribeViewModel::class.java]
        shown = viewModel
        compose.runOnUiThread { compose.activity.setContent { SubscribeDialog(viewModel) } }
        compose.runOnUiThread { viewModel.showDrawer() }
        val placeholder = compose.activity.getString(R.string.feed_or_site_url)
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(placeholder).performTextInput(npub)
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodes(hasSetTextAction() and hasText(npub)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.search)).performClick()
        val refused = compose.activity.getString(R.string.already_subscribed)
        compose.waitUntil(WAIT_MILLIS) { viewModel.subscribeUiState.value.errorMessage == refused }
        assertNull("no preview for a source that is already stored", viewModel.subscribeUiState.value.feed)
        assertEquals("the relay must not be asked", 0, relay.requestsReceived())
        assertTrue("the search box is unlocked again", !viewModel.subscribeUiState.value.lockLinkInput)
    }
}
