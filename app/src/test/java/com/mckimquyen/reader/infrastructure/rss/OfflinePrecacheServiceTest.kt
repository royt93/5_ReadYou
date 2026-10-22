package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.mckimquyen.reader.domain.model.article.FullContentPolicy
import com.mckimquyen.reader.domain.model.article.ArticleBody
import com.mckimquyen.reader.domain.model.article.toListItem
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class OfflinePrecacheServiceTest {

    private lateinit var context: Context
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val feedDao = mockk<com.mckimquyen.reader.domain.repository.FeedDao>(relaxed = true)
    private val rssHelper = mockk<RssHelper>(relaxed = true)
    private val imageLoader = mockk<ImageLoader>(relaxed = true)
    private lateinit var service: OfflinePrecacheService

    private val sampleArticle = Article(
        id = "art_precache_1",
        date = Date(),
        title = "NASA James Webb Space Telescope",
        author = "NASA",
        rawDescription = "<p>Brief summary</p>",
        shortDescription = "JWST discovery",
        fullContent = null,
        img = "https://example.com/banner.jpg",
        link = "https://example.com/jwst",
        feedId = "feed_space",
        accountId = 1,
        isUnread = true,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coEvery { feedDao.queryById(any()) } returns null
        val realService = OfflinePrecacheService(
            context = context,
            articleDao = articleDao,
            rssHelper = rssHelper,
            imageLoader = imageLoader,
            ioDispatcher = Dispatchers.Unconfined,
            feedDao = feedDao,
        )
        service = spyk(realService)
    }

    @Test
    fun precache_parsesFullContentAndEnqueuesImages_whenOnWifi() = runBlocking {
        every { service.isOnWifi() } returns true
        coEvery { articleDao.queryUnreadArticleList(accountId = 1, limit = 50) } returns listOf(sampleArticle.toListItem())
        coEvery { rssHelper.parseFullContent("https://example.com/jwst", "NASA James Webb Space Telescope") } returns
                "<p>Full content text</p><img src=\"https://example.com/photo.png\"/>"
        every { rssHelper.extractImageUrls(any()) } returns listOf("https://example.com/photo.png")

        val cachedCount = service.precacheLatestUnread(accountId = 1, limit = 50)

        assertEquals(1, cachedCount)
        coVerify(exactly = 1) { articleDao.updateFullContent(1, "art_precache_1", match { it.contains("Full content") }) }
        coVerify(exactly = 0) { articleDao.update(any()) }
        // 2 images enqueued: banner.jpg + photo.png
        io.mockk.verify(exactly = 2) { imageLoader.enqueue(any()) }
    }

    @Test
    fun precache_usesHardenedFetchForWebFeedArticles() = runBlocking {
        every { service.isOnWifi() } returns true
        coEvery { articleDao.queryUnreadArticleList(accountId = 1, limit = 50) } returns listOf(sampleArticle.toListItem())
        coEvery { feedDao.queryById("feed_space") } returns com.mckimquyen.reader.domain.model.feed.Feed(
            id = "feed_space", name = "Space", url = "https://example.com", groupId = "g", accountId = 1,
            sourceType = com.mckimquyen.reader.domain.model.feed.Feed.WEB_SOURCE_TYPE,
            sourceUrl = "https://example.com",
        )
        coEvery { rssHelper.parseFullContent(sampleArticle.link, sampleArticle.title, any()) } returns "<p>Full content text</p>"
        every { rssHelper.extractImageUrls(any()) } returns emptyList()

        service.precacheLatestUnread(accountId = 1, limit = 50)

        coVerify {
            rssHelper.parseFullContent(
                sampleArticle.link, sampleArticle.title,
                com.mckimquyen.reader.domain.model.feed.Feed.WEB_SOURCE_TYPE,
            )
        }
        io.mockk.verify(exactly = 0) { imageLoader.enqueue(any()) }
    }

    @Test
    fun precache_skipsExecution_whenNotOnWifi() = runBlocking {
        every { service.isOnWifi() } returns false

        val cachedCount = service.precacheLatestUnread(accountId = 1)

        assertEquals(0, cachedCount)
        coVerify(exactly = 0) { articleDao.queryUnreadArticleList(any(), any()) }
    }

    @Test
    fun precache_reusesFullContentAndDoesNotOverwriteOtherColumns() = runBlocking {
        every { service.isOnWifi() } returns true
        coEvery { articleDao.queryUnreadArticleList(1, 50) } returns listOf(sampleArticle.toListItem())
        coEvery { articleDao.queryArticleBody(1, sampleArticle.id) } returns ArticleBody(sampleArticle.id, "Original raw", "Cached body")
        assertEquals(1, service.precacheLatestUnread(1))
        coVerify(exactly = 0) { rssHelper.parseFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { articleDao.update(any()) }
        coVerify(exactly = 0) { articleDao.updateFullContent(any(), any(), any()) }
    }

    @Test
    fun precache_rejectsOversizeAndContinuesWithFollowingArticle() = runBlocking {
        every { service.isOnWifi() } returns true
        val following = sampleArticle.copy(id = "following", link = "https://example.com/following")
        val sentinel = "PRECACHE_PRIVATE_SENTINEL"
        val oversized = sentinel + "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1 - sentinel.length)
        coEvery { articleDao.queryUnreadArticleList(1, OfflinePrecacheService.DEFAULT_LIMIT) } returns
            listOf(sampleArticle.toListItem(), following.toListItem())
        coEvery { articleDao.queryArticleBody(1, any()) } returns null
        coEvery { rssHelper.parseFullContent(sampleArticle.link, sampleArticle.title, any()) } returns oversized
        coEvery { rssHelper.parseFullContent(following.link, following.title, any()) } returns "<p>Accepted</p>"
        org.robolectric.shadows.ShadowLog.clear()
        assertEquals(1, service.precacheLatestUnread(1))
        coVerify(exactly = 0) { articleDao.updateFullContent(1, sampleArticle.id, any()) }
        coVerify(exactly = 1) { articleDao.updateFullContent(1, following.id, "<p>Accepted</p>") }
        coVerify(exactly = 0) { articleDao.update(any()) }
        io.mockk.verify(exactly = 0) { rssHelper.extractImageUrls(oversized) }
        val logs = org.robolectric.shadows.ShadowLog.getLogs().joinToString("\n") { it.msg }
        assertTrue(logs.contains("source=OfflinePrecache"))
        assertTrue(logs.contains("bytes=${FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1}"))
        org.junit.Assert.assertFalse(logs.contains(sentinel))
    }

    @Test
    fun precache_acceptsExactCapFetchedContent() = runBlocking {
        every { service.isOnWifi() } returns true
        val content = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES)
        coEvery { articleDao.queryUnreadArticleList(1, OfflinePrecacheService.DEFAULT_LIMIT) } returns listOf(sampleArticle.toListItem())
        coEvery { articleDao.queryArticleBody(1, sampleArticle.id) } returns null
        coEvery { rssHelper.parseFullContent(sampleArticle.link, sampleArticle.title, any()) } returns content
        assertEquals(1, service.precacheLatestUnread(1))
        coVerify(exactly = 1) { articleDao.updateFullContent(1, sampleArticle.id, content) }
    }

    @Test
    fun precache_preservesOversizeHistoricalCache() = runBlocking {
        every { service.isOnWifi() } returns true
        val historical = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1)
        coEvery { articleDao.queryUnreadArticleList(1, OfflinePrecacheService.DEFAULT_LIMIT) } returns listOf(sampleArticle.toListItem())
        coEvery { articleDao.queryArticleBody(1, sampleArticle.id) } returns ArticleBody(sampleArticle.id, "raw", historical)
        assertEquals(1, service.precacheLatestUnread(1))
        coVerify(exactly = 0) { rssHelper.parseFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { articleDao.updateFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { articleDao.update(any()) }
    }

    @Test
    fun extractImageUrls_filtersInvalidAndDuplicates() {
        val helper = RssHelper(context, Dispatchers.Unconfined, mockk(relaxed = true))
        val html = """
            <p>Content</p>
            <img src="https://example.com/1.jpg"/>
            <img src="https://example.com/1.jpg"/>
            <img src="https://example.com/pixel.gif"/>
            <img src="data:image/png;base64,abc"/>
        """.trimIndent()

        val urls = helper.extractImageUrls(html)
        assertEquals(listOf("https://example.com/1.jpg"), urls)
    }
}
