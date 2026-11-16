package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.AccountType
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What happens to the Add Source flow when something interrupts it: a second Search while one is running, the dialog
 * closing mid-search, the relay answering late, the network coming back. These run on the JVM with fake services.
 * NOT covered here: a real screen rotation or recreate of the Activity, the app going to the background, and the process
 * being killed (this view model keeps nothing across process death; the typed text and preview are lost then).
 * A rotation test on a phone was tried on the Samsung A50s (2026-11-15) and dropped: MockK cannot fake the interfaces
 * this view model needs on Android 11 (IncompatibleClassChangeError), and opening the real dialog needs a tap on the "+"
 * button, which the no-tap rule forbids. A future test needs a debug-only entry point that opens the dialog.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelInterruptionTest {
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

    private fun TestScope.startSearch(text: String) {
        viewModel.inputLink(text)
        viewModel.search()
        runCurrent()
    }

    @Test fun closingTheDialogWhileTheRelayIsStillAnswering_showsNoErrorAndNoStalePreviewLater() = runTest(dispatcher) {
        val gate = CompletableDeferred<List<NostrEvent>>()
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } coAnswers { NostrFetchResult(gate.await(), null) }
        startSearch(npub)
        assertTrue("the search is waiting on the relay", state.lockLinkInput)

        viewModel.hideDrawer()
        viewModel.reset()
        runCurrent()
        gate.complete(notes(NostrTestEvents.PLAIN))
        advanceUntilIdle()

        assertNull("a preview that arrives after the dialog was reset must not appear", state.feed)
        assertTrue(state.articles.isEmpty())
        assertEquals("", state.errorMessage)
        assertFalse(state.lockLinkInput)
        assertTrue(state.isSearchPage)
    }

    @Test fun aSecondSearch_whileTheFirstIsStillWaiting_neverShowsTheFirstAnswer() = runTest(dispatcher) {
        val slowGate = CompletableDeferred<List<NostrEvent>>()
        val otherHex = NostrTestEvents.STRANGER
        val otherNpub = requireNotNull(NostrKeys.hexToNpub(otherHex))
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } coAnswers { NostrFetchResult(slowGate.await(), null) }
        coEvery { relay.fetch(otherHex) } returns NostrFetchResult(notes(NostrTestEvents.FOREIGN), null)

        startSearch(npub)
        viewModel.inputLink(otherNpub)
        viewModel.search()
        runCurrent()
        slowGate.complete(notes(NostrTestEvents.PLAIN))
        advanceUntilIdle()

        assertEquals("only the newest search may fill the preview", "nostr:$otherNpub", state.feed?.url)
        assertEquals(listOf("Written by somebody else"), state.articles.map { it.title })
    }

    @Test fun aCancelledSearch_isNotReportedAsAnError() = runTest(dispatcher) {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } coAnswers { delay(60_000); NostrFetchResult.EMPTY }
        startSearch(npub)
        viewModel.reset()
        advanceUntilIdle()
        assertEquals("cancelling is the user's choice, not a failure", "", state.errorMessage)
    }

    @Test fun afterTheNetworkFails_searchingAgainWorks_andShowsThePreview() = runTest(dispatcher) {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult.EMPTY
        startSearch(npub)
        advanceUntilIdle()
        assertNull(state.feed)
        assertFalse("the box must be editable again after a failure", state.lockLinkInput)

        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN), null)
        viewModel.search()
        advanceUntilIdle()

        assertEquals("nostr:$npub", state.feed?.url)
        assertEquals("", state.errorMessage)
        coVerify(exactly = 2) { relay.fetch(NostrTestEvents.AUTHOR) }
    }
}
