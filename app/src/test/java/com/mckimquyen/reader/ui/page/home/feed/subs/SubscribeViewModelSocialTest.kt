package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelSocialTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
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
            nostrRelayClient = mockk(relaxed = true),
            blueskyApiClient = mockk(relaxed = true),
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    private fun feedAt(url: String, name: String = "Social") = FeedWithArticle(
        Feed(id = "feed_1", name = name, url = url, groupId = "group_1", accountId = 1),
        listOf(Article(id = "a1", date = Date(), title = "Hello", rawDescription = "", shortDescription = "", link = "$url/1", feedId = "feed_1", accountId = 1)),
    )

    @Test
    fun aMastodonHandle_isSearchedAsItsRssAddress() = runTest(dispatcher) {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss, "Mastodon")

        viewModel.inputLink("@Mastodon@mastodon.social")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
        coVerify(exactly = 0) { resolver.resolveToRssUrl(any()) }
        assertEquals(rss, viewModel.subscribeUiState.value.feed?.url)
        assertEquals(1, viewModel.subscribeUiState.value.articles.size)
        assertFalse(viewModel.subscribeUiState.value.webPreview)
    }

    @Test
    fun aBlueskyHandle_isNotMangledByFormatUrl_andIsSearchedAsItsRssAddress() = runTest(dispatcher) {
        val rss = "https://bsky.app/profile/bsky.app/rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss, "@bsky.app - Bluesky")

        viewModel.inputLink("@bsky.app")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
        coVerify(exactly = 0) { rssHelper.searchFeed("https://@bsky.app") }
        assertEquals(rss, viewModel.subscribeUiState.value.feed?.url)
    }

    @Test
    fun aBlueskyProfileLink_isSearchedAsItsRssAddress() = runTest(dispatcher) {
        val rss = "https://bsky.app/profile/bsky.app/rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss)

        viewModel.inputLink("https://bsky.app/profile/bsky.app")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
    }

    @Test
    fun theAlreadySubscribedCheck_usesTheRssAddress() = runTest(dispatcher) {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { localService.isFeedExist(rss) } returns true

        viewModel.inputLink("@Mastodon@mastodon.social")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { localService.isFeedExist(rss) }
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test
    fun ifTheGuessedRssCannotBeRead_theOldFlowRunsOnWhatTheUserTyped() = runTest(dispatcher) {
        val typed = "https://mastodon.social/@Mastodon"
        val guessed = "https://mastodon.social/@Mastodon.rss"
        val resolved = "https://mastodon.social/@Mastodon/feed"
        coEvery { rssHelper.searchFeed(guessed) } throws IllegalStateException("blocked")
        coEvery { rssHelper.searchFeed(typed) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(typed) } returns resolved
        coEvery { rssHelper.searchFeed(resolved) } returns feedAt(resolved)

        viewModel.inputLink(typed)
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(guessed) }
        coVerify(exactly = 1) { rssHelper.searchFeed(typed) }
        coVerify(exactly = 1) { resolver.resolveToRssUrl(typed) }
        assertEquals(resolved, viewModel.subscribeUiState.value.feed?.url)
    }

    @Test
    fun theFallback_formatsWhatTheUserTyped_exactlyAsTheOldFlowDid() = runTest(dispatcher) {
        val typed = "@Mastodon@mastodon.social"
        val guessed = "https://mastodon.social/@Mastodon.rss"
        val formatted = "https://@Mastodon@mastodon.social"
        coEvery { rssHelper.searchFeed(guessed) } throws IllegalStateException("blocked")
        coEvery { rssHelper.searchFeed(any()) } throws IllegalStateException("not a feed")

        viewModel.inputLink(typed)
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(formatted) }
        coVerify(exactly = 0) { rssHelper.searchFeed(typed) }
    }

    @Test
    fun anOrdinaryAddress_followsTheOldFlowUntouched() = runTest(dispatcher) {
        val url = "https://example.com/feed.xml"
        coEvery { rssHelper.searchFeed(url) } returns feedAt(url, "Example")

        viewModel.inputLink("example.com/feed.xml")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(url) }
        assertEquals(url, viewModel.subscribeUiState.value.feed?.url)
    }

    @Test
    fun aMediumHandleStyleLink_stillGoesThroughTheNewsletterFlow() = runTest(dispatcher) {
        val typed = "https://medium.com/@someone"
        coEvery { rssHelper.searchFeed(typed) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(typed) } returns "https://medium.com/feed/@someone"
        coEvery { rssHelper.searchFeed("https://medium.com/feed/@someone") } returns feedAt("https://medium.com/feed/@someone", "Someone")

        viewModel.inputLink(typed)
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 0) { rssHelper.searchFeed("https://medium.com/@someone.rss") }
        coVerify(exactly = 1) { resolver.resolveToRssUrl(typed) }
    }
}
