package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.toListItem
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrNoWebFetchTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: RssHelper

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body><p>should never be read</p></body></html>"))
        helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun parseFullContent_forANostrArticle_returnsEmptyWithoutOpeningAConnection() = runBlocking {
        val result = helper.parseFullContent(server.url("/note").toString(), "t", Feed.NOSTR_SOURCE_TYPE)
        assertEquals("", result)
        assertEquals(0, server.requestCount)
    }

    @Test fun parseFullContent_forAnRssArticle_stillFetches() = runBlocking {
        helper.parseFullContent(server.url("/page").toString(), "t", Feed.RSS_SOURCE_TYPE)
        assertEquals(1, server.requestCount)
    }

    @Test fun queryRssIcon_forANostrFeed_doesNotWriteAFaviconAddress() = runBlocking {
        val feedDao = mockk<FeedDao>(relaxed = true)
        val feed = Feed(id = "n", name = "Nostr", url = "https://relay-host.test/x", groupId = "g", accountId = 1, sourceType = Feed.NOSTR_SOURCE_TYPE)
        helper.queryRssIcon(feedDao, feed)
        coVerify(exactly = 0) { feedDao.update(any()) }
    }

    @Test fun precache_forANostrArticle_neverFetchesFullContentOrImages() = runBlocking {
        val article = Article(
            id = "a1", date = Date(), title = "t", rawDescription = "<p>x</p>", shortDescription = "x",
            img = "https://example.com/banner.jpg", link = server.url("/note").toString(), feedId = "nostr-feed", accountId = 1,
        )
        val articleDao = mockk<ArticleDao>(relaxed = true)
        val feedDao = mockk<FeedDao>(relaxed = true)
        val rss = mockk<RssHelper>(relaxed = true)
        val images = mockk<ImageLoader>(relaxed = true)
        coEvery { feedDao.queryById("nostr-feed") } returns Feed(id = "nostr-feed", name = "N", url = "nostr:x", groupId = "g", accountId = 1, sourceType = Feed.NOSTR_SOURCE_TYPE)
        coEvery { articleDao.queryUnreadArticleList(accountId = 1, limit = 50) } returns listOf(article.toListItem())
        val service = spyk(OfflinePrecacheService(ApplicationProvider.getApplicationContext(), articleDao, rss, images, Dispatchers.Unconfined, feedDao))
        every { service.isOnWifi() } returns true

        service.precacheLatestUnread(1)

        coVerify(exactly = 0) { rss.parseFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { rss.extractImageUrls(any()) }
        io.mockk.verify(exactly = 0) { images.enqueue(any()) }
        assertTrue(server.requestCount == 0)
    }
}
