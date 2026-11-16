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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlueskyNoWebFetchTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: RssHelper

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body><p>should never be read</p></body></html>"))
        helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun parseFullContent_forABlueskyArticle_returnsEmptyWithoutOpeningAConnection() = runBlocking {
        assertEquals("", helper.parseFullContent(server.url("/post").toString(), "t", Feed.BLUESKY_SOURCE_TYPE))
        assertEquals(0, server.requestCount)
    }

    @Test fun queryRssIcon_forABlueskyFeed_doesNotWriteAFaviconAddress() = runBlocking {
        val feedDao = mockk<FeedDao>(relaxed = true)
        val feed = Feed(id = "b", name = "Bluesky", url = "https://x.test/x", groupId = "g", accountId = 1, sourceType = Feed.BLUESKY_SOURCE_TYPE)
        helper.queryRssIcon(feedDao, feed)
        coVerify(exactly = 0) { feedDao.update(any()) }
    }

    @Test fun precache_forABlueskyArticle_neverFetchesFullContentOrImages() = runBlocking {
        val article = Article(
            id = "a1", date = Date(), title = "t", rawDescription = "<p>x</p>", shortDescription = "x",
            img = "https://example.com/banner.jpg", link = server.url("/post").toString(), feedId = "bsky-feed", accountId = 1,
        )
        val articleDao = mockk<ArticleDao>(relaxed = true)
        val feedDao = mockk<FeedDao>(relaxed = true)
        val rss = mockk<RssHelper>(relaxed = true)
        val images = mockk<ImageLoader>(relaxed = true)
        coEvery { feedDao.queryById("bsky-feed") } returns Feed(id = "bsky-feed", name = "B", url = "bsky:x", groupId = "g", accountId = 1, sourceType = Feed.BLUESKY_SOURCE_TYPE)
        coEvery { articleDao.queryUnreadArticleList(accountId = 1, limit = 50) } returns listOf(article.toListItem())
        val service = spyk(OfflinePrecacheService(ApplicationProvider.getApplicationContext(), articleDao, rss, images, Dispatchers.Unconfined, feedDao))
        every { service.isOnWifi() } returns true

        service.precacheLatestUnread(1)

        coVerify(exactly = 0) { rss.parseFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { rss.extractImageUrls(any()) }
        io.mockk.verify(exactly = 0) { images.enqueue(any()) }
        assertEquals(0, server.requestCount)
    }
}
