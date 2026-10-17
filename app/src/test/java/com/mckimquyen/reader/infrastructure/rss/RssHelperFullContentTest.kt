package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssHelperFullContentTest {
    private val server = MockWebServer()
    private val helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.IO, OkHttpClient())

    @Before
    fun setUp() { server.start() }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun rejectsAudioEvenWhenBodyLooksLikeHtml() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(HTML))
        assertEquals("", helper.parseFullContent(server.url("/segment.mp3").toString(), TITLE))
    }

    @Test
    fun preservesReadableHtml() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(HTML))
        val result = helper.parseFullContent(server.url("/story").toString(), TITLE)
        assertTrue(result.contains(TEXT))
    }

    @Test
    fun rejectsOversizedChunkedHtml() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setChunkedBody("<p>" + "x".repeat(MAX_BYTES + 1) + "</p>", 8192))
        assertEquals("", helper.parseFullContent(server.url("/large").toString(), TITLE))
    }

    @Test
    fun rejectsHtmlContainingBinaryNulls() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(HTML.replace(TEXT, TEXT + "\u0000")))
        assertEquals("", helper.parseFullContent(server.url("/binary").toString(), TITLE))
    }

    @Test
    fun rejectsHttpErrorHtml() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Content-Type", "text/html").setBody(HTML))
        assertEquals("", helper.parseFullContent(server.url("/error").toString(), TITLE))
    }

    @Test
    fun webFullContentRejectsLoopbackBeforeSharedClientCanFetch() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(HTML))
        val result = runCatching {
            helper.parseFullContent(server.url("/private").toString(), TITLE, com.mckimquyen.reader.domain.model.feed.Feed.WEB_SOURCE_TYPE)
        }
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    private companion object {
        const val MAX_BYTES = 1_048_576
        const val TITLE = "A report"
        const val TEXT = "A detailed report about public transport, local communities and new services."
        val HTML = "<html><body><article><h1>$TITLE</h1><p>${TEXT.repeat(10)}</p></article></body></html>"
    }
}
