package com.mckimquyen.reader.ui.page.home.feed.drawer.feed

import android.app.Application
import androidx.compose.material.ExperimentalMaterialApi
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.RssSv
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NEW_ADDRESS = "bsky:bob.bsky.social"
private const val UI_STATE_FIELD = "_feedOptionUiState"

/**
 * The address menu of a source. The sync of a web, Nostr or Bluesky source reads sourceUrl before url, so an address typed in
 * this menu must reach both columns; a plain RSS source has no sourceUrl and must keep none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedOptionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private lateinit var viewModel: FeedOptionViewModel // built in @Before, after the main dispatcher is set

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { rssService.get() } returns localService
        coEvery { localService.pullGroups() } returns flowOf(mutableListOf<com.mckimquyen.reader.domain.model.group.Group>())
        viewModel = FeedOptionViewModel(rssService, dispatcher, dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun feed(url: String, sourceUrl: String?, sourceType: String = Feed.RSS_SOURCE_TYPE) = Feed(
        id = "feed-1", name = "Name", icon = "icon", url = url, groupId = "group-1", accountId = 7,
        isNotification = true, isFullContent = true, sourceType = sourceType, sourceUrl = sourceUrl,
    )

    /**
     * Puts [feed] into the view model's state, as the drawer would after loading it. It does NOT call showDrawer(): that call runs
     * ModalBottomSheetState.show() on a scope that outlives the test, and measured on 2026-11-17 it left the Compose clock of the
     * JVM busy, so Compose test classes that ran later in the same JVM failed with AppNotIdleException (SubscribeDialogTypingTest
     * with NativeSwipeRefreshWidgetTest: red 6/6 with the call, green 5/5 without). The state is written by reflection because
     * the view model has no public way to load a feed without opening the drawer.
     */
    @OptIn(ExperimentalMaterialApi::class)
    private fun TestScope.open(feed: Feed) {
        val field = FeedOptionViewModel::class.java.getDeclaredField(UI_STATE_FIELD).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") // the field is a MutableStateFlow<FeedOptionUiState>; a wrong type fails loudly right here
        val state = field.get(viewModel) as MutableStateFlow<FeedOptionUiState>
        state.value = state.value.copy(feed = feed, selectedGroupId = feed.groupId)
        runCurrent()
    }

    private fun TestScope.change(address: String) {
        viewModel.inputNewUrl(address)
        viewModel.changeFeedUrl()
        advanceUntilIdle()
    }

    private fun saved(): Feed {
        val slot = slot<Feed>()
        coVerify(exactly = 1) { localService.updateFeed(capture(slot)) }
        return slot.captured
    }

    @Test fun anRssFeed_changesOnlyItsUrl_andKeepsAnEmptySourceUrl() = runTest(dispatcher) {
        open(feed("https://old.example/rss", null))
        change("https://new.example/rss")
        assertEquals("https://new.example/rss", saved().url)
        assertNull(saved().sourceUrl)
    }

    @Test fun aWebFeed_changesBothColumns() = runTest(dispatcher) {
        open(feed("https://old.example/news", "https://old.example/news", Feed.WEB_SOURCE_TYPE))
        change("https://new.example/news")
        assertEquals("https://new.example/news", saved().url)
        assertEquals("https://new.example/news", saved().sourceUrl)
    }

    @Test fun aNewBlueskyFeed_changesBothColumns_andLosesTheStoredDid() = runTest(dispatcher) {
        open(feed("bsky:alice.bsky.social", "bsky:did:plc:abc", Feed.BLUESKY_SOURCE_TYPE))
        change(NEW_ADDRESS)
        assertEquals(NEW_ADDRESS, saved().url)
        assertEquals("the DID of the old account no longer describes the new address", NEW_ADDRESS, saved().sourceUrl)
    }

    @Test fun aNostrFeed_changesBothColumns() = runTest(dispatcher) {
        open(feed("nostr:npub1old", "nostr:npub1old", Feed.NOSTR_SOURCE_TYPE))
        change("nostr:npub1new")
        assertEquals("nostr:npub1new", saved().url)
        assertEquals("nostr:npub1new", saved().sourceUrl)
    }

    @Test fun everyOtherField_isKept() = runTest(dispatcher) {
        val before = feed("bsky:alice.bsky.social", "bsky:did:plc:abc", Feed.BLUESKY_SOURCE_TYPE)
        open(before)
        change(NEW_ADDRESS)
        val after = saved()
        assertEquals(before.copy(url = NEW_ADDRESS, sourceUrl = NEW_ADDRESS), after)
    }

    @Test fun theDialogCloses_afterSaving() = runTest(dispatcher) {
        open(feed("https://old.example/rss", null))
        viewModel.showFeedUrlDialog()
        change("https://new.example/rss")
        assertFalse(viewModel.feedOptionUiState.value.changeUrlDialogVisible)
    }

    @Test fun withoutALoadedFeed_nothingIsSaved() = runTest(dispatcher) {
        change("https://new.example/rss")
        coVerify(exactly = 0) { localService.updateFeed(any()) }
    }

    // Recorded, not required: the address is stored exactly as typed (no trim, no normalizing).
    @Test fun theTypedAddressIsUsedAsIs() = runTest(dispatcher) {
        open(feed("https://old.example/rss", null))
        change("  https://new.example/rss ")
        assertEquals("  https://new.example/rss ", saved().url)
    }
}
