package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebArticleCandidate
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedRefreshResult
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRssSvWebFeedSyncTest {
    private val articles = mockk<ArticleDao>(relaxed = true)
    private val health = mockk<FeedHealthDao>(relaxed = true)
    private val rss = mockk<RssHelper>(relaxed = true)
    private val scraper = mockk<WebFeedScraper>()
    private val filters = mockk<com.mckimquyen.reader.infrastructure.filter.SmartFilterManager> {
        io.mockk.every { applyRules(any()) } answers { firstArg() }
    }
    private val service = LocalRssSv(
        context = ApplicationProvider.getApplicationContext(), articleDao = articles,
        feedDao = mockk(relaxed = true), rssHelper = rss, notificationHelper = mockk(relaxed = true),
        accountDao = mockk(relaxed = true), groupDao = mockk(relaxed = true), feedHealthDao = health,
        ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
        workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true),
        smartFilterManager = filters, webFeedScraper = scraper,
    )
    private val feed = Feed(id = "web-feed", name = "News", url = SOURCE_URL, groupId = "group", accountId = 1,
        icon = "icon", sourceType = Feed.WEB_SOURCE_TYPE, sourceUrl = SOURCE_URL)

    @Test
    fun webRefreshUsesSourceUrlAndKeepsStoredFeedIdentity() = runBlocking {
        coEvery { scraper.refresh(SOURCE_URL) } returns WebFeedRefreshResult.Success(candidates())
        val result = service.syncSingleFeed(feed)
        assertEquals(feed.id, result?.feed?.id)
        assertEquals(listOf("Report 1", "Report 2", "Report 3"), result?.articles?.map { it.title })
        assertTrue(result?.articles?.all { it.feedId == feed.id && it.accountId == feed.accountId } == true)
        coVerify(exactly = 0) { rss.queryRssXml(any(), any()) }
        coVerify { health.upsert(match { it.feedId == feed.id && it.lastErrorType == com.mckimquyen.reader.domain.model.feed.FeedErrorType.NONE }) }
    }

    @Test
    fun ordinaryRssUsesExistingParser() = runBlocking {
        val rssFeed = feed.copy(sourceType = Feed.RSS_SOURCE_TYPE, sourceUrl = null)
        coEvery { rss.queryRssXml(rssFeed, any()) } returns emptyList()
        assertNotNull(service.syncSingleFeed(rssFeed))
        coVerify(exactly = 1) { rss.queryRssXml(rssFeed, any()) }
        coVerify(exactly = 0) { scraper.refresh(any()) }
    }

    @Test
    fun failedWebRefreshRecordsHealthAndDoesNotBlockNextRssFeed() = runBlocking {
        coEvery { scraper.refresh(SOURCE_URL) } returns WebFeedRefreshResult.Failure(WebFeedDiscoveryResult.Timeout)
        assertNull(service.syncSingleFeed(feed))
        coVerify { health.upsert(match { it.feedId == feed.id && it.lastErrorType == com.mckimquyen.reader.domain.model.feed.FeedErrorType.TIMEOUT && it.lastErrorTime != null }) }
        val rssFeed = feed.copy(id = "rss-feed", sourceType = Feed.RSS_SOURCE_TYPE, sourceUrl = null)
        coEvery { rss.queryRssXml(rssFeed, any()) } returns emptyList()
        assertNotNull(service.syncSingleFeed(rssFeed))
    }

    @Test
    fun malformedCandidateDoesNotDropValidSiblings() = runBlocking {
        val invalid = WebArticleCandidate("", "https://example.test/invalid".toHttpUrl(), null)
        coEvery { scraper.refresh(SOURCE_URL) } returns WebFeedRefreshResult.Success(candidates() + invalid)
        assertEquals(3, service.syncSingleFeed(feed)?.articles?.size)
    }

    @Test
    fun repeatedRefreshAndOneNewLinkUseRealRoomDedupe() = runBlocking {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            com.mckimquyen.reader.infrastructure.db.AndroidDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val dao = database.articleDao()
            database.groupDao().insert(com.mckimquyen.reader.domain.model.group.Group(id = feed.groupId, name = "Test", accountId = feed.accountId))
            database.feedDao().insert(feed)
            coEvery { scraper.refresh(SOURCE_URL) } returns WebFeedRefreshResult.Success(candidates())
            val first = requireNotNull(service.syncSingleFeed(feed)).articles
            assertEquals(3, dao.insertListIfNotExist(first).size)
            val repeated = requireNotNull(service.syncSingleFeed(feed)).articles
            assertEquals(0, dao.insertListIfNotExist(repeated).size)
            coEvery { scraper.refresh(SOURCE_URL) } returns WebFeedRefreshResult.Success(candidates() + WebArticleCandidate("Report 4", "https://example.test/story/4".toHttpUrl(), null))
            val refreshed = requireNotNull(service.syncSingleFeed(feed)).articles
            assertEquals(listOf("Report 4"), dao.insertListIfNotExist(refreshed).map { it.title })
            assertEquals(4, dao.queryLatestUnread(feed.accountId, 50).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun retryUsesExistingDedupePipeline() = runBlocking {
        coEvery { scraper.refresh(SOURCE_URL) } returns WebFeedRefreshResult.Success(candidates())
        coEvery { articles.insertListIfNotExist(any()) } returns emptyList()
        assertTrue(service.retryFeedSync(feed))
        coVerify { articles.insertListIfNotExist(match { it.map(Article::link) == candidates().map { c -> c.url.toString() } }) }
    }

    private fun candidates() = (1..3).map { WebArticleCandidate("Report $it", "https://example.test/story/$it".toHttpUrl(), null) }

    private companion object { const val SOURCE_URL = "https://example.test/news" }
}
