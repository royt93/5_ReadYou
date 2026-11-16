package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFetchResult
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads
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
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelBlueskyTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private val api = mockk<BlueskyApiClient>()
    private val rssUrl = "https://bsky.app/profile/alice.bsky.social/rss"
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
            androidStringsHelper = strings, webFeedScraper = scraper, newsletterUrlResolver = resolver,
            nostrRelayClient = mockk(relaxed = true), blueskyApiClient = api,
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    private fun found(vararg items: String, name: String? = "Alice A", avatar: String? = "https://cdn.bsky.app/img/avatar/a@jpeg") =
        BlueskyOutcome.Found(BlueskyFetchResult(BlueskyTestPayloads.array(*items), BlueskyTestPayloads.DID, BlueskyTestPayloads.HANDLE, name, avatar))

    private fun feedAt(url: String) = FeedWithArticle(
        Feed(id = "feed_1", name = "RSS name", url = url, groupId = "group_1", accountId = 1),
        listOf(Article(id = "a1", date = Date(), title = "rss item", rawDescription = "", shortDescription = "", link = "$url/1", feedId = "feed_1", accountId = 1)),
    )

    private val state get() = viewModel.subscribeUiState.value

    private fun TestScope.search(text: String) {
        viewModel.inputLink(text)
        viewModel.search()
        advanceUntilIdle()
    }

    @Test fun aHandle_isReadThroughTheApi_andShowsTheDisplayNameAndTheAvatar() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("@alice.bsky.social")
        assertEquals("Bluesky · Alice A", state.feed?.name)
        assertEquals("bsky:alice.bsky.social", state.feed?.url)
        assertEquals("bsky:alice.bsky.social", state.feed?.sourceUrl)
        assertEquals(Feed.BLUESKY_SOURCE_TYPE, state.feed?.sourceType)
        assertEquals("https://cdn.bsky.app/img/avatar/a@jpeg", state.feed?.icon)
        assertEquals(listOf("hello"), state.articles.map { it.title })
        assertTrue(state.lockLinkInput)
        assertFalse(state.webPreview)
        assertEquals("", state.errorMessage)
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test fun withoutADisplayName_theNameIsTheHandle_andWithoutAnAvatarThereIsNoIcon() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"), name = null, avatar = null)
        search("@alice.bsky.social")
        assertEquals("Bluesky · @alice.bsky.social", state.feed?.name)
        assertNull(state.feed?.icon)
    }

    @Test fun aProfileLink_andADidLink_alsoUseTheApi() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("https://bsky.app/profile/alice.bsky.social")
        assertEquals("bsky:alice.bsky.social", state.feed?.url)
        coEvery { api.fetch("did:plc:AbC123") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        viewModel.reset(); viewModel.init()
        search("https://bsky.app/profile/did:plc:AbC123")
        assertEquals("bsky:did:plc:AbC123", state.feed?.url)
    }

    @Test fun whileTheApiAnswers_theBoxIsLocked_andShowsSearching() = runTest(dispatcher) {
        val gate = CompletableDeferred<BlueskyOutcome>()
        coEvery { api.fetch(any()) } coAnswers { gate.await() }
        viewModel.inputLink("@alice.bsky.social"); viewModel.search(); runCurrent()
        assertTrue(state.lockLinkInput)
        assertEquals(strings.getString(R.string.searching), state.title)
        gate.complete(found(BlueskyTestPayloads.post("3k1", "hello")))
        advanceUntilIdle()
        assertEquals("Bluesky · Alice A", state.feed?.name)
    }

    @Test fun whenTheApiFails_theOldRssFlowRuns_withTheSameAddressAsBefore() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns BlueskyOutcome.Failed
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
        assertEquals(rssUrl, state.feed?.url)
        assertEquals(Feed.RSS_SOURCE_TYPE, state.feed?.sourceType)
    }

    @Test fun whenTheApiThrows_theOldRssFlowRuns() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } throws java.io.IOException("boom")
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
    }

    @Test fun whenTheApiHasNoUsablePosts_theOldRssFlowRuns() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.repost("r1"))
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
    }

    @Test fun aNotFoundAccount_isAnError_neverTriedAsRss_andTheBoxKeepsWhatWasTyped() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns BlueskyOutcome.NotFound
        search("@alice.bsky.social")
        assertEquals(strings.getString(R.string.web_feed_fetch_failed), state.errorMessage)
        assertEquals("@alice.bsky.social", state.linkContent)
        assertFalse(state.lockLinkInput)
        assertNull(state.feed)
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test fun anAnswerThatIsNeitherFoundNorNotFound_fallsBackToRss_notACrash() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns mockk<BlueskyOutcome>()
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
    }

    @Test fun anAlreadySubscribedSource_isReportedWithoutCallingTheApi() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("bsky:alice.bsky.social") } returns true
        search("@alice.bsky.social")
        assertEquals(strings.getString(R.string.already_subscribed), state.errorMessage)
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun aNonLocalAccount_neverUsesTheApi() = runTest(dispatcher) {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Fever.id)
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun aMastodonHandle_neverTouchesTheApi() = runTest(dispatcher) {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss)
        search("@Mastodon@mastodon.social")
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun closingTheDialogWhileTheApiAnswers_showsNoErrorAndNoLatePreview() = runTest(dispatcher) {
        val gate = CompletableDeferred<BlueskyOutcome>()
        coEvery { api.fetch(any()) } coAnswers { gate.await() }
        viewModel.inputLink("@alice.bsky.social"); viewModel.search(); runCurrent()
        viewModel.hideDrawer(); viewModel.reset(); runCurrent()
        gate.complete(found(BlueskyTestPayloads.post("3k1", "hello")))
        advanceUntilIdle()
        assertNull(state.feed)
        assertEquals("", state.errorMessage)
        assertFalse(state.lockLinkInput)
    }

    @Test fun aSecretKeyIsStillBlockedFirst_andNeverReachesTheApi() = runTest(dispatcher) {
        search("nsec1abc")
        coVerify(exactly = 0) { api.fetch(any()) }
        assertEquals("", state.linkContent)
    }

    @Test fun subscribing_keepsTheAvatarAsTheIcon_andNeverAsksForFullContent() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("@alice.bsky.social")
        viewModel.changeParseFullContentPreset()
        viewModel.subscribe()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            localService.subscribe(
                match { it.sourceType == Feed.BLUESKY_SOURCE_TYPE && it.url == "bsky:alice.bsky.social" && it.icon == "https://cdn.bsky.app/img/avatar/a@jpeg" && !it.isFullContent },
                any(),
            )
        }
    }
}
