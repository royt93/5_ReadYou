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

/**
 * Pasting an nprofile into the Add Source box must do exactly what pasting the same person's npub does, and the relays named in the
 * nprofile must never be used. The strings were computed by an independent encoder (the app did not produce their checksums).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelNostrNprofileTest {
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
    private val npub = "npub1mlcawle2vuw97dscxundkg6phev0atsa5t0vakzrys8hk5pt5evssm7a0a"
    private val nprofile = "nprofile1qqsdluwh0u4xw8zlxcvrwfkmydqmuk874cw69hkwmppjgrmm2q46vkgv8s759"
    private val nprofileWithRelays = "nprofile1qqsdluwh0u4xw8zlxcvrwfkmydqmuk874cw69hkwmppjgrmm2q46vkgpzemhxue69uhk2anfdshx27rpd4cxcefwvdhk6qgnwaen5te0xyerwt3s9cczuvf68yunjwg4vju7n"
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

    @Test fun anNprofile_showsTheSamePreviewAsItsNpub() = runTest(dispatcher) {
        search(nprofile)
        val feed = requireNotNull(state.feed)
        assertEquals(Feed.NOSTR_SOURCE_TYPE, feed.sourceType)
        assertEquals("the stored address is the npub form, never the nprofile", "nostr:$npub", feed.url)
        assertEquals("nostr:$npub", feed.sourceUrl)
        assertEquals("nostr:$npub", state.linkContent)
        assertEquals(listOf("Hello nostr, first note", "Older note"), state.articles.map { it.title })
        assertTrue(state.lockLinkInput)
        assertEquals("", state.errorMessage)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }

    @Test fun anNprofileWithRelayHints_asksTheRelayClientForTheKeyOnly() = runTest(dispatcher) {
        search(nprofileWithRelays)
        assertEquals("nostr:$npub", state.feed?.url)
        // fetch(authorHex) has no place for a relay: the only thing it can receive is the 64-character key.
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        coVerify(exactly = 1) { relay.fetch(any()) }
        verifyNothingWentToTheWeb()
    }

    @Test fun anNprofileOfASubscribedNpub_isRefusedWithoutAskingTheRelay() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("nostr:$npub") } returns true
        search(nprofileWithRelays)
        coVerify(exactly = 1) { localService.isFeedExist("nostr:$npub") }
        coVerify(exactly = 0) { relay.fetch(any()) }
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.already_subscribed), state.errorMessage)
        assertFalse(state.lockLinkInput)
        verifyNothingWentToTheWeb()
    }

    @Test fun aNostrUriNprofile_isRecognised() = runTest(dispatcher) {
        search("  nostr:$nprofile ")
        assertEquals("nostr:$npub", state.feed?.url)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }
}
