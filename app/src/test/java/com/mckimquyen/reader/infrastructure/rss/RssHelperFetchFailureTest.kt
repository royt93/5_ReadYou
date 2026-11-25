package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssHelperFetchFailureTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: RssHelper

    @Before fun setUp() {
        ShadowLog.clear()
        server = MockWebServer().apply { start() }
        helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    private fun feed() = Feed(id = "f", name = "F", url = server.url("/x").toString(), groupId = "g", accountId = 1)

    private val rss = """<rss version="2.0"><channel><title>T</title><link>https://e.test</link><description>d</description>
        <item><title>Pinned old</title><link>https://e.test/old</link><pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate></item>
        <item><title>New 1</title><link>https://e.test/n1</link><pubDate>Mon, 03 Nov 2026 00:00:00 GMT</pubDate></item>
        <item><title>New 2</title><link>https://e.test/n2</link><pubDate>Tue, 04 Nov 2026 00:00:00 GMT</pubDate></item>
        </channel></rss>"""

    @Test fun anHttpErrorIsAFailureNotAnEmptyFeed() {
        // It used to return an empty list, so the feed was recorded as a healthy fetch.
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>oops</html>"))
        assertThrows(java.io.IOException::class.java) { runBlocking { helper.queryRssXml(feed(), null) } }
    }

    @Test fun aNotFoundIsAFailureToo() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertThrows(java.io.IOException::class.java) { runBlocking { helper.queryRssXml(feed(), null) } }
    }

    @Test fun aGoodFeedStillParses() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(rss))
        assertEquals(3, helper.queryRssXml(feed(), null).size)
    }

    @Test fun aPinnedOldEntryOnTopDoesNotHideTheEntriesAfterIt() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(rss))
        // latestLink is the newest by date in the DB ("New 2"), which sits last in the document.
        val got = helper.queryRssXml(feed(), latestLink = "https://e.test/n2")
        assertEquals(listOf("Pinned old", "New 1", "New 2"), got.map { it.title })
    }
}
