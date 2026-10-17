package com.mckimquyen.reader.infrastructure.rss.scraper

import java.net.InetAddress
import okio.buffer
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class WebFeedScraperTest {
    private lateinit var server: MockWebServer
    private lateinit var scraper: WebFeedScraper
    private val sourceHost = "www.example.test"
    private lateinit var clientAddress: InetAddress

    @Before
    fun setUp() {
        server = httpsServer().apply { start() }
        clientAddress = InetAddress.getByName(server.hostName)
        scraper = WebFeedScraper(
            httpClient = httpClient(),
            parser = WebFeedParser(),
            maxResponseBytes = 4096,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun discoverReturnsPreviewForValidStaticArticleList() = runBlocking {
        server.enqueue(htmlResponse(articleListHtml()))

        val result = scraper.discover(server.url("/world").toString().replace("localhost", sourceHost))

        assertTrue(result is WebFeedDiscoveryResult.Preview)
        val preview = result as WebFeedDiscoveryResult.Preview
        assertEquals("Example Journal", preview.siteTitle)
        assertEquals(3, preview.articles.size)
    }

    @Test
    fun discoverRejectsRssDocumentAndDoesNotPersistAnything() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/rss+xml").setBody("<rss/>"))

        val result = scraper.discover(server.url("/feed.xml").toString().replace("localhost", sourceHost))

        assertEquals(WebFeedDiscoveryResult.NonHtml, result)
    }

    @Test
    fun discoverRejectsRedirectWithoutFollowingLocation() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/other"))

        val result = scraper.discover(server.url("/redirect").toString().replace("localhost", sourceHost))

        assertEquals(WebFeedDiscoveryResult.RedirectRejected, result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun discoverRejectsNonSuccessHttpStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = scraper.discover(server.url("/error").toString().replace("localhost", sourceHost))

        assertEquals(WebFeedDiscoveryResult.HttpError(503), result)
    }

    @Test
    fun discoverRejectsNonHtmlContentType() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))

        val result = scraper.discover(server.url("/json").toString().replace("localhost", sourceHost))

        assertEquals(WebFeedDiscoveryResult.NonHtml, result)
    }

    @Test
    fun discoverRejectsResponseLargerThanConfiguredLimit() = runBlocking {
        server.enqueue(htmlResponse(articleListHtml().repeat(20)))

        val result = scraper.discover(server.url("/large").toString().replace("localhost", sourceHost))

        assertEquals(WebFeedDiscoveryResult.ResponseTooLarge, result)
    }

    @Test
    fun discoverMapsInsufficientCandidateListToTypedFailure() = runBlocking {
        server.enqueue(htmlResponse("<html><body><article><h2><a href='/one'>Only one</a></h2></article></body></html>"))

        val result = scraper.discover(server.url("/small").toString().replace("localhost", sourceHost))

        assertEquals(WebFeedDiscoveryResult.TooFewCandidates, result)
    }

    @Test
    fun midBodyIoFailureReturnsTypedErrorAndClosesBody() = runBlocking {
        var closed = false
        val source = object : okio.Source {
            override fun read(sink: okio.Buffer, byteCount: Long): Long = throw java.io.IOException("broken body")
            override fun timeout() = okio.Timeout.NONE
            override fun close() { closed = true }
        }
        val body = object : okhttp3.ResponseBody() {
            private val buffered = source.buffer()
            override fun contentType() = "text/html".toMediaType()
            override fun contentLength() = -1L
            override fun source() = buffered
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").header("Content-Type", "text/html").body(body).build()
        }.build()
        try {
            val factory = mockk<WebFeedHttpClient> { coEvery { create(any()) } returns client }
            assertEquals(WebFeedDiscoveryResult.HttpError(0), WebFeedScraper(factory, WebFeedParser()).discover("https://example.test/news"))
            assertTrue(closed)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun cancelWhileReadingBodyCancelsHttpCall() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val released = java.util.concurrent.CountDownLatch(1)
        val cancelled = java.util.concurrent.CountDownLatch(1)
        val client = OkHttpClient.Builder().eventListener(object : okhttp3.EventListener() {
            override fun canceled(call: okhttp3.Call) { cancelled.countDown() }
        }).addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").header("Content-Type", "text/html").body(object : okhttp3.ResponseBody() {
                    private val buffered = object : okio.Source {
                        override fun read(sink: okio.Buffer, byteCount: Long): Long {
                            entered.countDown()
                            released.await()
                            return -1
                        }
                        override fun timeout() = okio.Timeout.NONE
                        override fun close() {}
                    }.buffer()
                    override fun contentType() = "text/html".toMediaType()
                    override fun contentLength() = -1L
                    override fun source() = buffered
                }).build()
        }.build()
        val factory = mockk<WebFeedHttpClient> { coEvery { create(any()) } returns client }
        val job = launch(kotlinx.coroutines.Dispatchers.IO) { WebFeedScraper(factory, WebFeedParser()).discover("https://example.test/news") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancel()
            assertTrue("Cancellation must reach HTTP call while body read is blocked", cancelled.await(2, TimeUnit.SECONDS))
        } finally {
            released.countDown()
            job.cancelAndJoin()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun httpClient(): WebFeedHttpClient = mockk {
        coEvery { create(any()) } returns client()
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .dns(Dns { listOf(clientAddress) })
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .hostnameVerifier { _, _ -> true }
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun htmlResponse(html: String) =
        MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(html)

    private fun articleListHtml() = """<html><head><title>Example Journal</title></head><body>
        <article><h2><a href="/story/1">First report</a></h2></article>
        <article><h2><a href="/story/2">Second report</a></h2></article>
        <article><h2><a href="/story/3">Third report</a></h2></article>
        </body></html>"""

    private fun httpsServer() = MockWebServer().apply { useHttps(serverCertificates.sslSocketFactory()) }

    private companion object {
        val serverCertificate = HeldCertificate.Builder().addSubjectAlternativeName("www.example.test").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(serverCertificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(serverCertificate.certificate).build()
    }
}
