package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
class RssHelperDescriptionTest {
    private lateinit var server: MockWebServer
    private lateinit var rssHelper: RssHelper

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        rssHelper = RssHelper(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = Dispatchers.Unconfined,
            okHttpClient = OkHttpClient(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkObject(RssDescriptionParser)
    }

    @Test
    fun searchFeed_mappingFailureSkipsOnlyBadEntry() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(
                """<rss version="2.0"><channel><title>Guardian</title><link>https://example.test</link><description>News</description>
                    <item><title>First</title><link>https://example.test/first</link><description>First summary</description></item>
                    <item><title>Malformed</title><link>https://example.test/bad</link><description>Bad summary</description></item>
                    <item><title>Last</title><link>https://example.test/last</link><description>Last summary</description></item>
                    </channel></rss>""".trimIndent()
            )
        )
        mockkObject(RssDescriptionParser)
        every { RssDescriptionParser.shortDescription(any()) } answers {
            firstArg<String?>().orEmpty().take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH)
        }
        every { RssDescriptionParser.shortDescription("Bad summary") } throws IllegalArgumentException("bad summary")

        val result = rssHelper.searchFeed(server.url("/guardian.xml").toString())

        assertEquals(listOf("First", "Last"), result.articles.map { it.title })
    }

    @Test
    fun searchFeed_malformedDescriptionFallsBackAndKeepsFollowingEntries() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(
                """<?xml version="1.0" encoding="UTF-8"?>
                    <rss version="2.0"><channel><title>Guardian</title><link>https://example.test</link><description>News</description>
                    <item><title>How a scandal engulfing Brazil’s top court could return the Bolsonaros to power</title><link>https://example.test/bad</link><description><![CDATA[<p>The judge who helped convict Jair Bolsonaro is caught up in a crisis that could benefit his son and damage Lula</p><ul><li><p><a href="https://example.test/a">Marina Silva on Flávio Bolsonaro: He will try to finish his father’s ‘perverse project’ to destroy the Amazon</a></p></li></ul><p>Thundering to a crowd of thousands of far-right supporters in São Paulo on 7 September, Brazil’s Independence Day, the senator Flávio Bolsonaro focused less on his opponent in the <a href="https://example.test/b">presidential election</a>, the leftist president Luiz Inácio Lula da Silva, than on someone who is not running at all.</p><p>“Whoever votes for Lula is voting for <a href="https://example.test/c">Alexandre de Moraes</a>,” Bolsonaro shouted, referring to the shaven-headed supreme court justice once <a href="https://example.test/d">described as “Voldemort”</a> by Elon Musk.</p> <a href="https://example.test/main">Continue reading...</a>]]></description></item>
                    <item><title>Valid</title><link>https://example.test/good</link><description><![CDATA[<p>A normal second article summary.</p>]]></description></item>
                    </channel></rss>""".trimIndent()
            )
        )

        val result: FeedWithArticle = rssHelper.searchFeed(server.url("/guardian.xml").toString())

        assertEquals(
            listOf("How a scandal engulfing Brazil’s top court could return the Bolsonaros to power", "Valid"),
            result.articles.map { it.title },
        )
        assertTrue(result.articles.first().shortDescription.contains("judge who helped convict Jair Bolsonaro"))
        assertEquals("A normal second article summary.", result.articles.last().shortDescription)
    }

    @Test
    fun queryRssXml_malformedDescriptionFallsBackAndKeepsFollowingEntries() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(
                """<rss version="2.0"><channel><title>Guardian</title><link>https://example.test</link><description>News</description>
                    <item><title>Malformed summary</title><link>https://example.test/bad</link><description><![CDATA[<p>The judge who helped convict Jair Bolsonaro is caught up in a crisis that could benefit his son and damage Lula</p><ul><li><p><a href="https://example.test/a">Marina Silva on Flávio Bolsonaro: He will try to finish his father’s ‘perverse project’ to destroy the Amazon</a></p></li></ul><p>Thundering to a crowd of thousands of far-right supporters in São Paulo on 7 September, Brazil’s Independence Day, the senator Flávio Bolsonaro focused less on his opponent in the <a href="https://example.test/b">presidential election</a>, the leftist president Luiz Inácio Lula da Silva, than on someone who is not running at all.</p><p>“Whoever votes for Lula is voting for <a href="https://example.test/c">Alexandre de Moraes</a>,” Bolsonaro shouted, referring to the shaven-headed supreme court justice once <a href="https://example.test/d">described as “Voldemort”</a> by Elon Musk.</p> <a href="https://example.test/main">Continue reading...</a>]]></description></item>
                    <item><title>Following summary</title><link>https://example.test/good</link><description><![CDATA[<p>A normal second article summary.</p>]]></description></item>
                    </channel></rss>""".trimIndent()
            )
        )
        val feed = Feed(
            id = "feed",
            name = "Guardian",
            url = server.url("/guardian.xml").toString(),
            groupId = "group",
            accountId = 1,
        )

        val articles = rssHelper.queryRssXml(feed, latestLink = null)

        assertEquals(listOf("Malformed summary", "Following summary"), articles.map { it.title })
        assertTrue(articles.first().shortDescription.contains("judge who helped convict Jair Bolsonaro"))
        assertEquals("A normal second article summary.", articles.last().shortDescription)
    }
}
