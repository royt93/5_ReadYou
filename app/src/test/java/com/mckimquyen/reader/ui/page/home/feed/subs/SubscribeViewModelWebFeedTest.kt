package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.spacerDollar
import androidx.datastore.preferences.core.edit
import com.mckimquyen.reader.ui.ext.putBlocking
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebArticleCandidate
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
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
class SubscribeViewModelWebFeedTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private lateinit var viewModel: SubscribeViewModel
    private var originalAccountType = 1

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        originalAccountType = context.currentAccountType
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id)
        assertEquals(AccountType.Local.id, context.currentAccountType)
        every { rssService.get() } returns localService
        every { rssService.get(AccountType.Local.id) } returns localService
        every { rssService.get() } returns localService
        coEvery { localService.pullGroups() } returns flowOf(mutableListOf(group))
        coEvery { localService.isFeedExist(any()) } returns false
        viewModel = SubscribeViewModel(context, mockk<OpmlSv>(relaxed = true), rssService, rssHelper, strings, scraper)
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    @Test
    fun htmlDiscoveryShowsPreviewWithoutPersistingUntilConfirmed() = runTest(dispatcher) {
        val candidates = sampleCandidates()
        coEvery { localService.isFeedExist(any()) } returns false
        coEvery { rssHelper.searchFeed(PAGE_URL) } throws IllegalArgumentException("not an RSS document")
        coEvery { scraper.discover(PAGE_URL) } returns WebFeedDiscoveryResult.Preview("Example Journal", candidates)
        viewModel.inputLink(PAGE_URL)

        viewModel.search()
        advanceUntilIdle()

        assertTrue(viewModel.subscribeUiState.value.webPreview)
        assertEquals("Example Journal", viewModel.subscribeUiState.value.feed?.name)
        assertEquals(candidates.map { it.title }, viewModel.subscribeUiState.value.articles.map { it.title })
        coVerify(exactly = 0) { localService.subscribe(any(), any()) }
    }

    @Test
    fun confirmPersistsVirtualFeedAndPreviewArticlesExactlyOnce() = runTest(dispatcher) {
        coEvery { localService.isFeedExist(any()) } returns false
        coEvery { rssHelper.searchFeed(PAGE_URL) } throws IllegalArgumentException("not an RSS document")
        coEvery { scraper.discover(PAGE_URL) } returns WebFeedDiscoveryResult.Preview("Example Journal", sampleCandidates())
        coEvery { localService.subscribe(any(), any()) } returns Unit
        viewModel.inputLink(PAGE_URL)
        viewModel.search()
        advanceUntilIdle()

        viewModel.confirmWebFeed()
        viewModel.confirmWebFeed()
        advanceUntilIdle()

        val feedSlot = slot<Feed>()
        val articleSlot = slot<List<Article>>()
        coVerify(exactly = 1) { localService.subscribe(any(), any()) }
        coVerify { localService.subscribe(capture(feedSlot), capture(articleSlot)) }
        val feed = feedSlot.captured
        val articles = articleSlot.captured
        assertEquals(Feed.WEB_SOURCE_TYPE, feed.sourceType)
        assertEquals(PAGE_URL, feed.sourceUrl)
        assertEquals("group_1", feed.groupId)
        assertEquals(context.currentAccountId, feed.accountId)
        assertEquals(3, articles.size)
        assertTrue(articles.all { it.feedId == feed.id && it.accountId == context.currentAccountId })
    }

    @Test
    fun cancelCannotResetPreviewWhileConfirmIsQueued() = runTest(dispatcher) {
        coEvery { rssHelper.searchFeed(PAGE_URL) } throws IllegalArgumentException("not RSS")
        coEvery { scraper.discover(PAGE_URL) } returns WebFeedDiscoveryResult.Preview("Example Journal", sampleCandidates())
        coEvery { localService.subscribe(any(), any()) } returns Unit
        viewModel.inputLink(PAGE_URL)
        viewModel.search()
        advanceUntilIdle()
        viewModel.confirmWebFeed()
        viewModel.cancelWebFeedPreview()
        assertTrue("Save already accepted; Cancel must not pretend to discard it", viewModel.subscribeUiState.value.webPreview)
        advanceUntilIdle()
        coVerify(exactly = 1) { localService.subscribe(any(), any()) }
    }

    @Test
    fun failedConfirmKeepsPreviewAndAllowsRetry() = runTest(dispatcher) {
        coEvery { rssHelper.searchFeed(PAGE_URL) } throws IllegalArgumentException("not RSS")
        coEvery { scraper.discover(PAGE_URL) } returns WebFeedDiscoveryResult.Preview("Example Journal", sampleCandidates())
        coEvery { localService.subscribe(any(), any()) } throws java.io.IOException("Save failed")
        viewModel.inputLink(PAGE_URL)
        viewModel.search()
        advanceUntilIdle()

        viewModel.confirmWebFeed()
        advanceUntilIdle()
        assertTrue(viewModel.subscribeUiState.value.webPreview)
        assertEquals("Save failed", viewModel.subscribeUiState.value.errorMessage)

        coEvery { localService.subscribe(any(), any()) } returns Unit
        viewModel.confirmWebFeed()
        advanceUntilIdle()
        coVerify(exactly = 2) { localService.subscribe(any(), any()) }
    }

    @Test
    fun cancelPreviewDoesNotPersistWebFeed() = runTest(dispatcher) {
        coEvery { localService.isFeedExist(any()) } returns false
        coEvery { rssHelper.searchFeed(PAGE_URL) } throws IllegalArgumentException("not an RSS document")
        coEvery { scraper.discover(PAGE_URL) } returns WebFeedDiscoveryResult.Preview("Example Journal", sampleCandidates())
        viewModel.inputLink(PAGE_URL)
        viewModel.search()
        advanceUntilIdle()

        viewModel.cancelWebFeedPreview()
        advanceUntilIdle()

        assertFalse(viewModel.subscribeUiState.value.webPreview)
        assertNull(viewModel.subscribeUiState.value.feed)
        coVerify(exactly = 0) { localService.subscribe(any(), any()) }
    }

    @Test
    fun nonLocalBackendRejectsWebPreviewWithoutSavingFakeFeed() = runTest(dispatcher) {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Fever.id)
        assertEquals(AccountType.Fever.id, context.currentAccountType)
        every { rssService.get() } returns mockk(relaxed = true)
        coEvery { localService.isFeedExist(any()) } returns false
        coEvery { rssHelper.searchFeed(PAGE_URL) } throws IllegalArgumentException("not an RSS document")
        coEvery { scraper.discover(PAGE_URL) } returns WebFeedDiscoveryResult.Preview("Example Journal", sampleCandidates())
        viewModel.inputLink(PAGE_URL)

        viewModel.search()
        advanceUntilIdle()

        assertFalse(viewModel.subscribeUiState.value.webPreview)
        assertTrue(viewModel.subscribeUiState.value.errorMessage.isNotBlank())
        coVerify(exactly = 0) { scraper.discover(PAGE_URL) }
    }

    @Test
    fun failedWebDiscoveryShowsErrorAndClearsLoading() = runTest(dispatcher) {
        coEvery { rssHelper.searchFeed(PAGE_URL) } throws IllegalArgumentException("not an RSS document")
        coEvery { scraper.discover(PAGE_URL) } returns WebFeedDiscoveryResult.TooFewCandidates
        viewModel.inputLink(PAGE_URL)

        viewModel.search()
        advanceUntilIdle()

        assertFalse(viewModel.subscribeUiState.value.lockLinkInput)
        assertFalse(viewModel.subscribeUiState.value.webPreview)
        assertTrue(viewModel.subscribeUiState.value.errorMessage.isNotBlank())
        coVerify(exactly = 0) { localService.subscribe(any(), any()) }
    }

    private fun sampleCandidates() = (1..3).map { index ->
        WebArticleCandidate("Article $index", "https://example.test/$index".toHttpUrl(), Date(index.toLong()))
    }

    private companion object {
        const val PAGE_URL = "https://example.test/news"
    }
}
