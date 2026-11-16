package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFetchResult
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrTestEvents
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelNostrTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val relay = mockk<NostrRelayClient>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private val npub = requireNotNull(NostrKeys.hexToNpub(NostrTestEvents.AUTHOR))
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
    private lateinit var viewModel: SubscribeViewModel
    private var originalAccountType = 1

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        originalAccountType = context.currentAccountType
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id)
        every { rssService.get() } returns localService
        every { rssService.get(AccountType.Local.id) } returns localService
        coEvery { localService.pullGroups() } returns flowOf(mutableListOf(group))
        coEvery { localService.isFeedExist(any()) } returns false
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN, NostrTestEvents.OLDER), null)
        viewModel = SubscribeViewModel(
            context = context, opmlService = mockk<OpmlSv>(relaxed = true), rssService = rssService, rssHelper = rssHelper,
            androidStringsHelper = strings, webFeedScraper = scraper, newsletterUrlResolver = resolver, nostrRelayClient = relay,
            blueskyApiClient = mockk(relaxed = true),
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    private fun notes(vararg json: String): List<NostrEvent> = json.map { requireNotNull(NostrEvent.parse(it)) }
    private val state get() = viewModel.subscribeUiState.value

    private fun TestScope.search(text: String) {
        viewModel.inputLink(text)
        viewModel.search()
        advanceUntilIdle()
    }

    private fun verifyNothingWentToTheWeb() {
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
        coVerify(exactly = 0) { resolver.resolveToRssUrl(any()) }
        coVerify(exactly = 0) { scraper.discover(any()) }
    }

    @Test fun anNpub_showsAPreviewOfItsNotes_throughTheRelayOnly() = runTest(dispatcher) {
        search(npub)
        val feed = requireNotNull(state.feed)
        assertEquals(Feed.NOSTR_SOURCE_TYPE, feed.sourceType)
        assertEquals("nostr:$npub", feed.url)
        assertEquals("nostr:$npub", feed.sourceUrl)
        assertEquals("Nostr · " + npub.take(8) + "…" + npub.takeLast(4), feed.name)
        assertEquals(listOf("Hello nostr, first note", "Older note"), state.articles.map { it.title })
        assertTrue(state.articles.all { it.feedId == feed.id })
        assertFalse(state.isSearchPage)
        assertFalse(state.webPreview)
        assertTrue(state.lockLinkInput)
        assertEquals("", state.errorMessage)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }

    @Test fun anNpub_withSpacesAndUppercase_isRecognisedToo() = runTest(dispatcher) {
        search("  " + npub.uppercase() + " \n")
        assertEquals("nostr:$npub", state.feed?.url)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }

    @Test fun uppercaseAndLowercaseNpubs_haveOneCanonicalFeedAddress() = runTest(dispatcher) {
        search(npub.uppercase())
        assertEquals("nostr:$npub", state.feed?.url)
        assertEquals("Nostr \u00b7 " + npub.take(8) + "\u2026" + npub.takeLast(4), state.feed?.name)
        viewModel.reset()
        coEvery { localService.isFeedExist("nostr:$npub") } returns false
        search(npub)
        assertEquals("nostr:$npub", state.feed?.url)
    }

    @Test fun theBoxShowsTheNostrAddress_notAnHttpsGuess() = runTest(dispatcher) {
        search(npub)
        assertEquals("nostr:$npub", state.linkContent)
    }

    @Test fun theAlreadySubscribedCheck_usesTheNostrAddress_andSkipsTheRelay() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("nostr:$npub") } returns true
        search(npub)
        coVerify(exactly = 1) { localService.isFeedExist("nostr:$npub") }
        coVerify(exactly = 0) { relay.fetch(any()) }
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.already_subscribed), state.errorMessage)
        assertFalse(state.lockLinkInput)
        verifyNothingWentToTheWeb()
    }

    @Test fun whenTheRelaysAnswerNothing_itIsAnError_andNothingFallsBackToTheWeb() = runTest(dispatcher) {
        coEvery { relay.fetch(any()) } returns NostrFetchResult.EMPTY
        search(npub)
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.web_feed_fetch_failed), state.errorMessage)
        assertEquals(null, state.feed)
        assertFalse(state.lockLinkInput)
        assertFalse(state.errorMessage.contains(npub))
        verifyNothingWentToTheWeb()
    }

    @Test fun whenTheRelayClientThrows_itIsAnError_andNothingFallsBackToTheWeb() = runTest(dispatcher) {
        coEvery { relay.fetch(any()) } throws java.io.IOException("socket closed")
        search(npub)
        assertEquals(null, state.feed)
        assertTrue(state.errorMessage.isNotEmpty())
        assertFalse(state.lockLinkInput)
        verifyNothingWentToTheWeb()
    }

    @Test fun aSecretKey_isRefused_clearedFromTheBox_andGoesNowhere() = runTest(dispatcher) {
        search(nsec)
        assertEquals("", state.linkContent)
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.web_feed_invalid_url), state.errorMessage)
        assertEquals(null, state.feed)
        assertFalse(state.lockLinkInput)
        coVerify(exactly = 0) { relay.fetch(any()) }
        verifyNothingWentToTheWeb()
    }

    @Test fun onANonLocalAccount_anNpubIsNotNostr_andTheOldFlowRunsUntouched() = runTest(dispatcher) {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Fever.id)
        coEvery { rssHelper.searchFeed(any()) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(any()) } returns null
        search(npub)
        coVerify(exactly = 0) { relay.fetch(any()) }
        coVerify(exactly = 1) { rssHelper.searchFeed("https://$npub") }
    }

    @Test fun resetAfterPreview_clearsNostrFeedAndArticles() = runTest(dispatcher) {
        search(npub)
        viewModel.hideDrawer()
        viewModel.reset()
        assertEquals(null, state.feed)
        assertTrue(state.articles.isEmpty())
        assertTrue(state.isSearchPage)
    }

    @Test fun anOrdinaryAddress_neverTouchesTheRelay() = runTest(dispatcher) {
        coEvery { rssHelper.searchFeed(any()) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(any()) } returns null
        coEvery { scraper.discover(any()) } returns com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult.Unsupported
        search("example.com/feed.xml")
        coVerify(exactly = 0) { relay.fetch(any()) }
    }

    // ---- subscribe
    @Test fun subscribe_savesTheNostrFeedInTheChosenGroup_withFullContentOff() = runTest(dispatcher) {
        search(npub)
        viewModel.selectedGroup("group_nostr")
        viewModel.changeParseFullContentPreset()
        assertTrue(state.parseFullContentPreset)
        viewModel.subscribe()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            localService.subscribe(
                match { it.sourceType == Feed.NOSTR_SOURCE_TYPE && it.groupId == "group_nostr" && !it.isFullContent && it.url == "nostr:$npub" },
                match { it.size == 2 },
            )
        }
        assertFalse(state.visible)
    }

    @Test fun subscribe_stillHonoursFullContentForAnOrdinaryFeed() = runTest(dispatcher) {
        coEvery { rssHelper.searchFeed("https://example.com/feed.xml") } returns com.mckimquyen.reader.domain.model.feed.FeedWithArticle(
            Feed(id = "f", name = "N", url = "https://example.com/feed.xml", groupId = "", accountId = 1), emptyList(),
        )
        search("https://example.com/feed.xml")
        viewModel.selectedGroup("group_1")
        viewModel.changeParseFullContentPreset()
        viewModel.subscribe()
        advanceUntilIdle()
        coVerify(exactly = 1) { localService.subscribe(match { it.isFullContent && it.sourceType == Feed.RSS_SOURCE_TYPE }, any()) }
    }
}
