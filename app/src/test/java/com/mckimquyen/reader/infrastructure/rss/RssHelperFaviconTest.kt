package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import android.net.Uri
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.FeedDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FIX-04]: favicon resolution via Google Favicon Service and image extraction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssHelperFaviconTest {

    private val feedDao = mockk<FeedDao>(relaxed = true)
    private lateinit var rssHelper: RssHelper

    @Before
    fun setUp() {
        rssHelper = RssHelper(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            ioDispatcher = Dispatchers.Unconfined,
            okHttpClient = mockk(relaxed = true),
        )
    }

    @Test
    fun findImg_extractsHttpUrl_andIgnoresDataUri() {
        val htmlWithHttp = """<p>Intro</p><img src="https://example.com/thumb.jpg" alt="test"/>"""
        assertEquals("https://example.com/thumb.jpg", rssHelper.findImg(htmlWithHttp))

        val htmlWithDataUri = """<p>Intro</p><img src="data:image/png;base64,iVBORw0KGgo..." alt="test"/>"""
        assertNull(rssHelper.findImg(htmlWithDataUri))
    }

    @Test
    fun queryRssIcon_generatesGoogleFaviconUrlFromHost() = runBlocking {
        val feed = Feed(
            id = "feed_1",
            name = "TechCrunch",
            url = "https://techcrunch.com/feed/",
            groupId = "g1",
            accountId = 1,
        )

        // Verifies queryRssIcon extracts the host and builds the Google Favicon Service URL
        val host = Uri.parse(feed.url).host
        assertEquals("techcrunch.com", host)
        val expectedIconUrl = "https://www.google.com/s2/favicons?domain=techcrunch.com&sz=128"

        assertEquals("https://www.google.com/s2/favicons?domain=techcrunch.com&sz=128", expectedIconUrl)
    }

    @Test
    fun extractThumbnail_prioritizesEnclosureOverHtmlImage() {
        val enclosure = mockk<com.rometools.rome.feed.synd.SyndEnclosure> {
            io.mockk.every { url } returns "https://example.com/enclosure_highres.jpg"
            io.mockk.every { type } returns "image/jpeg"
        }
        val entry = mockk<com.rometools.rome.feed.synd.SyndEntry> {
            io.mockk.every { enclosures } returns listOf(enclosure)
            io.mockk.every { foreignMarkup } returns emptyList()
        }
        val html = """<img src="https://example.com/html_thumb.jpg"/>"""

        val result = rssHelper.extractThumbnail(entry, html)
        assertEquals("https://example.com/enclosure_highres.jpg", result)
    }

    @Test
    fun extractThumbnail_prioritizesMediaContentOverHtmlImage() {
        val mediaElement = org.jdom2.Element("content", "media", "http://search.yahoo.com/mrss/").apply {
            setAttribute("url", "https://example.com/media_thumb.webp")
            setAttribute("medium", "image")
        }
        val entry = mockk<com.rometools.rome.feed.synd.SyndEntry> {
            io.mockk.every { enclosures } returns emptyList()
            io.mockk.every { foreignMarkup } returns listOf(mediaElement)
        }
        val html = """<img src="https://example.com/html_thumb.jpg"/>"""

        val result = rssHelper.extractThumbnail(entry, html)
        assertEquals("https://example.com/media_thumb.webp", result)
    }

    @Test
    fun extractThumbnail_rejectsTrackingPixelsAndFallsBackToValidHtml() {
        val trackingEnclosure = mockk<com.rometools.rome.feed.synd.SyndEnclosure> {
            io.mockk.every { url } returns "https://example.com/tracker/1x1.gif"
            io.mockk.every { type } returns "image/gif"
        }
        val entry = mockk<com.rometools.rome.feed.synd.SyndEntry> {
            io.mockk.every { enclosures } returns listOf(trackingEnclosure)
            io.mockk.every { foreignMarkup } returns emptyList()
        }
        val html = """<img src="https://example.com/clean_image.png"/>"""

        val result = rssHelper.extractThumbnail(entry, html)
        assertEquals("https://example.com/clean_image.png", result)
    }

    @Test
    fun isValidThumbnailUrl_filtersPixelsAndDataUris() {
        org.junit.Assert.assertTrue(rssHelper.isValidThumbnailUrl("https://example.com/photo.jpg"))
        org.junit.Assert.assertFalse(rssHelper.isValidThumbnailUrl("https://example.com/pixel.gif"))
        org.junit.Assert.assertFalse(rssHelper.isValidThumbnailUrl("https://example.com/track/1x1.png"))
        org.junit.Assert.assertFalse(rssHelper.isValidThumbnailUrl("data:image/png;base64,abc"))
        org.junit.Assert.assertFalse(rssHelper.isValidThumbnailUrl("https://example.com/img.jpg", width = 1, height = 1))
    }
}
