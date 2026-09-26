package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
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
        val realService = OfflinePrecacheService(
            context = context,
            articleDao = articleDao,
            rssHelper = rssHelper,
            imageLoader = imageLoader,
            ioDispatcher = Dispatchers.Unconfined,
        )
        service = spyk(realService)
    }

    @Test
    fun precache_parsesFullContentAndEnqueuesImages_whenOnWifi() = runBlocking {
        every { service.isOnWifi() } returns true
        coEvery { articleDao.queryLatestUnread(accountId = 1, limit = 50) } returns listOf(sampleArticle)
        coEvery { rssHelper.parseFullContent("https://example.com/jwst", "NASA James Webb Space Telescope") } returns
                "<p>Full content text</p><img src=\"https://example.com/photo.png\"/>"
        every { rssHelper.extractImageUrls(any()) } returns listOf("https://example.com/photo.png")

        val cachedCount = service.precacheLatestUnread(accountId = 1, limit = 50)

        assertEquals(1, cachedCount)
        coVerify(exactly = 1) { articleDao.update(match { it.id == "art_precache_1" && it.fullContent?.contains("Full content") == true }) }
        // 2 images enqueued: banner.jpg + photo.png
        io.mockk.verify(exactly = 2) { imageLoader.enqueue(any()) }
    }

    @Test
    fun precache_skipsExecution_whenNotOnWifi() = runBlocking {
        every { service.isOnWifi() } returns false

        val cachedCount = service.precacheLatestUnread(accountId = 1)

        assertEquals(0, cachedCount)
        coVerify(exactly = 0) { articleDao.queryLatestUnread(any(), any()) }
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
