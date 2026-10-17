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
class SubscribeViewModelNewsletterTest {
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
            context = context,
            opmlService = mockk<OpmlSv>(relaxed = true),
            rssService = rssService,
            rssHelper = rssHelper,
            androidStringsHelper = strings,
            webFeedScraper = scraper,
            newsletterUrlResolver = resolver,
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    @Test
    fun search_resolvesNewsletterUrlWhenDirectRssSearchFails() = runTest(dispatcher) {
        val inputWebUrl = "https://danluu.substack.com"
        val resolvedRssUrl = "https://danluu.substack.com/feed"
        val expectedFeed = Feed(id = "feed_1", name = "Dan Luu", url = resolvedRssUrl, groupId = "group_1", accountId = 1)
        val expectedArticle = Article(
            id = "art_1",
            date = Date(),
            title = "Computer Latency",
            rawDescription = "",
            shortDescription = "",
            link = "https://danluu.substack.com/p/latency",
            feedId = "feed_1",
            accountId = 1
        )

        // 1. First search with inputWebUrl fails
        coEvery { rssHelper.searchFeed(inputWebUrl) } throws IllegalArgumentException("Not an XML feed")
        // 2. Resolver turns it into resolvedRssUrl
        coEvery { resolver.resolveToRssUrl(inputWebUrl) } returns resolvedRssUrl
        // 3. Second search with resolvedRssUrl succeeds
        coEvery { rssHelper.searchFeed(resolvedRssUrl) } returns FeedWithArticle(expectedFeed, listOf(expectedArticle))

        viewModel.inputLink(inputWebUrl)
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { resolver.resolveToRssUrl(inputWebUrl) }
        coVerify(exactly = 1) { rssHelper.searchFeed(resolvedRssUrl) }

        val uiState = viewModel.subscribeUiState.value
        assertEquals("Dan Luu", uiState.feed?.name)
        assertEquals(resolvedRssUrl, uiState.feed?.url)
        assertEquals(1, uiState.articles.size)
        assertEquals("Computer Latency", uiState.articles.first().title)
        assertFalse(uiState.webPreview)
    }
}
