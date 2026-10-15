package com.mckimquyen.reader.infrastructure.rss.scraper

import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
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
    private var clientFactoryCalls = 0
    private lateinit var clientAddress: InetAddress

    @Before
    fun setUp() {
        server = httpsServer().apply { start() }
        clientAddress = InetAddress.getByName(server.hostName)
        scraper = WebFeedScraper(
            clientFactory = { url ->
                clientFactoryCalls++
                client(url)
            },
            parser = WebFeedParser,
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
        assertEquals("HTTPS request client constructed once with DNS pinned for validated URL", 1, clientFactoryCalls)
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

    private fun client(validatedUrl: String): OkHttpClient {
        val pinnedUrl = validatedUrl.toHttpUrl()
        return WebFeedHttpClient.newBuilder()
            .dns(Dns { host ->
                assertEquals(pinnedUrl.host, host)
                listOf(clientAddress)
            })
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .hostnameVerifier { _, _ -> true }
        .followRedirects(false)
        .followSslRedirects(false)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

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
