package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssHelperSanitizerTest {

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
    }

    @Test
    fun searchFeed_cleansEmailJunkAndTrackingFromEntries() = runBlocking {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Tech Newsletter</title>
                    <link>https://example.com</link>
                    <description>Tech thoughts</description>
                    <item>
                        <title>Newsletter Edition 1</title>
                        <link>https://example.com/post-1</link>
                        <description><![CDATA[
                            <p>Great insight on compilers.</p>
                            <img src="https://open.substack.com/open.gif" width="1" height="1" />
                            <div class="subscription-widget-wrap">
                                <form action="/subscribe"><input type="email"/><button>Subscribe</button></form>
                            </div>
                            <p><a href="https://example.com/unsubscribe">Unsubscribe</a></p>
                        ]]></description>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        server.enqueue(MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(rssXml))

        val result = rssHelper.searchFeed(server.url("/feed.xml").toString())
        val article = result.articles.first()

        assertTrue(article.rawDescription.contains("Great insight on compilers."))
        assertFalse(article.rawDescription.contains("open.substack.com"))
        assertFalse(article.rawDescription.contains("subscription-widget-wrap"))
        assertFalse(article.rawDescription.contains("Unsubscribe"))
    }

    @Test
    fun parseFullContent_cleansTrackingUrlAndPixels() = runBlocking {
        val fullHtml = """
            <!DOCTYPE html>
            <html><body><article>
                <h1>Article Title</h1>
                <p>Real article text with enough content for readability extraction.</p>
                <img src="https://stats.com/pixel.gif" width="1" height="1" />
                <script>badScript();</script>
            </article></body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(fullHtml))

        val parsed = rssHelper.parseFullContent(
            "${server.url("/article")}?utm_source=twitter&fbclid=abc",
            "Article Title",
        )
        val request = server.takeRequest()

        assertTrue(parsed.contains("Real article text"))
        assertFalse(parsed.contains("badScript"))
        assertFalse(parsed.contains("pixel.gif"))
        assertTrue(request.path == "/article")
    }

    @Test
    fun parseFullContent_cleansEmailJunkAndTrackingFromFullArticle() = runBlocking {
        val fullHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Full Article</title></head>
            <body>
                <article>
                    <h1>Article Title</h1>
                    <p>Core paragraphs of deep tech writing.</p>
                    <img src="https://email.ghost.org/track/pixel.png" width="1" height="1" />
                    <div class="gh-post-upgrade-cta">
                        <a href="/upgrade">Upgrade now</a>
                    </div>
                    <p>More paragraphs of interesting text.</p>
                    <p><a href="https://ghost.org/unsubscribe">Unsubscribe</a></p>
                </article>
            </body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(fullHtml))

        val parsed = rssHelper.parseFullContent(server.url("/article").toString(), "Article Title")

        assertTrue(parsed.contains("Core paragraphs of deep tech writing."))
        assertTrue(parsed.contains("More paragraphs of interesting text."))
        assertFalse(parsed.contains("email.ghost.org"))
        assertFalse(parsed.contains("gh-post-upgrade-cta"))
        assertFalse(parsed.contains("Unsubscribe"))
    }
}
