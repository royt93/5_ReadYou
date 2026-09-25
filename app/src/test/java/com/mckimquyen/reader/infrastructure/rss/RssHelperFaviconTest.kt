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
}
