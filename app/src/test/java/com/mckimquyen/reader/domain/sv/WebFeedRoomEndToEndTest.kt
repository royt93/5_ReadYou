package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedHttpClient
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedParser
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebFeedRoomEndToEndTest {
    private lateinit var server: MockWebServer
    private lateinit var database: AndroidDatabase
    private lateinit var service: LocalRssSv
    private lateinit var client: OkHttpClient
    private lateinit var feed: Feed

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory())
            start()
        }
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        client = testClient()
        val httpClient = mockk<WebFeedHttpClient> { coEvery { create(any()) } returns client }
        val filters = mockk<SmartFilterManager> { every { applyRules(any()) } answers { firstArg() } }
        service = LocalRssSv(
            context = ApplicationProvider.getApplicationContext(), articleDao = database.articleDao(),
            feedDao = database.feedDao(), rssHelper = mockk(relaxed = true), notificationHelper = mockk(relaxed = true),
            accountDao = database.accountDao(), groupDao = database.groupDao(), feedHealthDao = database.feedHealthDao(),
            ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
            workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true), smartFilterManager = filters,
            webFeedScraper = WebFeedScraper(httpClient, WebFeedParser()),
            smartCollectionMatcher = mockk(relaxed = true),
        )
        val sourceUrl = server.url("/news").toString().replace("localhost", HOST)
        feed = Feed(id = "web-feed", name = "Example", url = sourceUrl, groupId = "group", accountId = 1,
            icon = "icon", sourceType = Feed.WEB_SOURCE_TYPE, sourceUrl = sourceUrl)
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun confirmedWebFeedRefreshAddsOnlyNewValidArticle() = runBlocking {
        database.groupDao().insert(Group(id = feed.groupId, name = "News", accountId = feed.accountId))
        val dao = database.articleDao()
        val preview = WebFeedScraper(
            httpClient = mockk<WebFeedHttpClient> { coEvery { create(any()) } returns client },
            parser = WebFeedParser(),
        )
        server.enqueue(html(listOf(1, 2, 3)))
        assertTrue(preview.discover(feed.url) is WebFeedDiscoveryResult.Preview)
        assertEquals(0, database.feedDao().queryAll(feed.accountId).size)

        server.enqueue(html(listOf(1, 2, 3)))
        service.subscribe(feed, requireNotNull(service.syncSingleFeed(feed)).articles)
        assertEquals(3, dao.queryLatestUnread(feed.accountId, 50).size)

        server.enqueue(html(listOf(1, 2, 3)))
        assertEquals(0, dao.insertListIfNotExist(requireNotNull(service.syncSingleFeed(feed)).articles).size)

        server.enqueue(html(listOf(1, 2, 3, 4), includeBadCandidate = true))
        val refreshed = dao.insertListIfNotExist(requireNotNull(service.syncSingleFeed(feed)).articles)
        assertEquals(listOf("Report 4"), refreshed.map { it.title })
        assertEquals(4, dao.queryLatestUnread(feed.accountId, 50).size)
        assertEquals(setOf(feed.id), dao.queryLatestUnread(feed.accountId, 50).map { it.feedId }.toSet())
    }

    private fun testClient() = OkHttpClient.Builder()
        .dns(Dns { listOf(InetAddress.getByName(server.hostName)) })
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .hostnameVerifier { _, _ -> true }
        .followRedirects(false)
        .build()

    private fun html(ids: List<Int>, includeBadCandidate: Boolean = false): MockResponse {
        val articles = ids.joinToString("\n") { """<article><h2><a href="/story/$it">Report $it</a></h2></article>""" }
        val bad = if (includeBadCandidate) """<article><h2>No link</h2></article>""" else ""
        return MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody("<html><head><title>Example</title></head><body>$articles$bad</body></html>")
    }

    private companion object {
        const val HOST = "www.example.test"
        val serverCertificate = HeldCertificate.Builder().addSubjectAlternativeName(HOST).build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(serverCertificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(serverCertificate.certificate).build()
    }
}
