package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import com.mckimquyen.reader.domain.model.article.FullContentPolicy
import androidx.test.core.app.ApplicationProvider
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
    fun rejectsResponseOneUtf8ByteOverPolicyLimitWithoutLoggingBody() = runBlocking<Unit> {
        val sentinel = "PRIVATE_CONTENT_SENTINEL"
        val body = (sentinel + "x".repeat(MAX_BYTES)).take(MAX_BYTES + 1)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setChunkedBody(body, 8192))
        assertEquals("", helper.parseFullContent(server.url("/over-one-byte").toString(), TITLE))
        val logText = ShadowLog.getLogs().joinToString("\\n") { it.msg }
        assertTrue(logText.contains("Reject oversized fullContent source=Readability"))
        assertTrue(logText.contains("bytes>${MAX_BYTES}"))
        assertFalse(logText.contains(sentinel))
    }

    @Test
    fun rejectsKnownOversizeAndLogsExactByteCountWithoutBody() = runBlocking {
        val sentinel = "KNOWN_LENGTH_PRIVATE_SENTINEL"
        val body = sentinel + "x".repeat(MAX_BYTES)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body))
        assertEquals("", helper.parseFullContent(server.url("/known-over-limit").toString(), TITLE))
        val logText = ShadowLog.getLogs().joinToString("\\n") { it.msg }
        assertTrue(logText.contains("bytes=${body.toByteArray(Charsets.UTF_8).size}"))
        assertFalse(logText.contains(sentinel))
    }

    @Test
    fun oversizeLogsRedactUrlForKnownAndChunkedBodies() = runBlocking {
        val payload = "x".repeat(MAX_BYTES + 1)
        for (chunked in listOf(false, true)) {
            val response = MockResponse().setHeader("Content-Type", "text/html")
            if (chunked) response.setChunkedBody(payload, CHUNK_BYTES) else response.setBody(payload)
            server.enqueue(response)
            val link = server.url("/private-story").newBuilder()
                .username("PRIVATE_USER").password("PRIVATE_PASSWORD")
                .addQueryParameter("token", "PRIVATE_QUERY_TOKEN")
                .fragment("PRIVATE_FRAGMENT").build().toString()
            ShadowLog.clear()
            assertEquals("", helper.parseFullContent(link, TITLE))
            val entries = ShadowLog.getLogs().filter { it.msg.contains("source=Readability") }
            assertTrue(entries.isNotEmpty())
            val logs = entries.joinToString("\n") { it.msg }
            assertTrue(logs.contains("/private-story"))
            assertTrue(logs.contains(if (chunked) "bytes>$MAX_BYTES" else "bytes=${MAX_BYTES + 1}"))
            for (secret in listOf("PRIVATE_USER", "PRIVATE_PASSWORD", "PRIVATE_QUERY_TOKEN", "PRIVATE_FRAGMENT")) {
                assertFalse(logs.contains(secret))
            }
            entries.forEach { org.junit.Assert.assertNull(it.throwable) }
        }
    }

    @Test
    fun acceptsReadableResponseNearPolicyLimit() = runBlocking {
        val sentence = "This is a detailed article sentence containing enough useful words for the readability parser."
        val paragraph = "<p>${sentence.repeat(PARAGRAPHS_PER_BLOCK)}</p>"
        val blocks = (MAX_BYTES / paragraph.toByteArray(Charsets.UTF_8).size) - 2
        val body = "<html><body><article><h1>$TITLE</h1>" + paragraph.repeat(blocks) + "</article></body></html>"
        assertTrue(FullContentPolicy.byteSize(body) <= MAX_BYTES)
        assertTrue(FullContentPolicy.byteSize(body) > MAX_BYTES - NEAR_LIMIT_MARGIN_BYTES)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body))
        val result = helper.parseFullContent(server.url("/at-limit").toString(), TITLE)
        assertTrue(result.isNotBlank())
        assertTrue(FullContentPolicy.byteSize(result) <= MAX_BYTES)
    }

    @Test
    fun rejectsBodyOverPolicyLimitByOneByte() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setChunkedBody("x".repeat(MAX_BYTES + 1), 8192))
        assertEquals("", helper.parseFullContent(server.url("/over-limit").toString(), TITLE))
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
        const val MAX_BYTES = FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES
        const val CHUNK_BYTES = 8192
        const val PARAGRAPHS_PER_BLOCK = 20
        const val NEAR_LIMIT_MARGIN_BYTES = 16_384
        const val TITLE = "A report"
        const val TEXT = "A detailed report about public transport, local communities and new services."
        val HTML = "<html><body><article><h1>$TITLE</h1><p>${TEXT.repeat(10)}</p></article></body></html>"
    }
}
