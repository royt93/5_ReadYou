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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssHelperNoTitleTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: RssHelper

    @Before fun setUp() {
        ShadowLog.clear()
        server = MockWebServer().apply { start() }
        helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    private fun feedOf(vararg items: String) = """<rss version="2.0"><channel><title>Social</title><link>https://example.test</link><description>d</description>${items.joinToString("")}</channel></rss>"""

    private fun enqueue(body: String) =
        server.enqueue(MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(body))

    private fun item(link: String, title: String? = null, desc: String? = null) =
        "<item>" + (title?.let { "<title>$it</title>" } ?: "") + "<link>$link</link>" + (desc?.let { "<description>$it</description>" } ?: "") + "</item>"

    private fun url() = server.url("/feed").toString()
    private fun logText() = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg}" }

    // ---- searchFeed (the preview)
    @Test fun searchFeed_keepsAnEntryThatHasNoTitle_usingTheBody() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", desc = "&lt;p&gt;Hello fediverse, this is a post&lt;/p&gt;")))
        val r = helper.searchFeed(url())
        assertEquals(1, r.articles.size)
        assertEquals("Hello fediverse, this is a post", r.articles[0].title)
    }

    @Test fun searchFeed_keepsTheFullBodyWhenOnlyTheTitleIsDerived() = runBlocking {
        val long = "word ".repeat(60)
        enqueue(feedOf(item("https://example.test/1", desc = "&lt;p&gt;$long&lt;/p&gt;")))
        val a = helper.searchFeed(url()).articles.single()
        assertTrue(a.title.endsWith("…"))
        assertTrue("the whole body stays in the article", a.rawDescription.length > a.title.length)
        assertTrue(a.rawDescription.contains("word word word"))
    }

    @Test fun searchFeed_fallbackTitleUsesTheSanitizedBody_notRawHtmlOrTrackingPixelText() = runBlocking {
        val tracking = "tinytrack.jpg?pixel"
        enqueue(feedOf(item(
            "https://example.test/1",
            desc = "&lt;p&gt;Readable post starts here&lt;img src=\"https://example.test/$tracking\" width=\"1\" height=\"1\"/&gt;&lt;/p&gt;",
        )))
        val article = helper.searchFeed(url()).articles.single()
        assertEquals("Readable post starts here", article.title)
        assertFalse(article.title.contains("tinytrack"))
        assertFalse(article.title.contains("img"))
    }

    @Test fun searchFeed_anEntryWithATitle_isNotChanged() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", title = "Real title", desc = "&lt;p&gt;Body that is different&lt;/p&gt;")))
        assertEquals("Real title", helper.searchFeed(url()).articles.single().title)
    }

    @Test fun searchFeed_aBlankTitle_isTreatedAsMissing() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", title = "   ", desc = "&lt;p&gt;Body text here&lt;/p&gt;")))
        assertEquals("Body text here", helper.searchFeed(url()).articles.single().title)
    }

    @Test fun searchFeed_noTitleAndNoBody_dropsOnlyThatEntry() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/empty"),
            item("https://example.test/ok", desc = "&lt;p&gt;Has a body&lt;/p&gt;"),
        ))
        val r = helper.searchFeed(url())
        assertEquals(listOf("Has a body"), r.articles.map { it.title })
    }

    @Test fun searchFeed_mixedFeed_keepsEveryUsableEntryInOrder() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/1", desc = "&lt;p&gt;First post&lt;/p&gt;"),
            item("https://example.test/2", title = "Second", desc = "&lt;p&gt;x&lt;/p&gt;"),
            item("https://example.test/3", desc = "&lt;p&gt;Third post&lt;/p&gt;"),
        ))
        assertEquals(listOf("First post", "Second", "Third post"), helper.searchFeed(url()).articles.map { it.title })
    }

    // ---- queryRssXml (the sync)
    @Test fun sync_keepsEntriesThatHaveNoTitle() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", desc = "&lt;p&gt;Sync post&lt;/p&gt;")))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), null)
        assertEquals(listOf("Sync post"), list.map { it.title })
    }

    @Test fun sync_aMalformedEntry_losesOnlyItself() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/a", title = "A", desc = "ok"),
            item("https://example.test/b"),                                    // no title, no body: cannot become an article
            item("https://example.test/c", title = "C", desc = "ok"),
        ))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), null)
        assertEquals("one bad entry must not empty the feed", listOf("A", "C"), list.map { it.title })
    }

    @Test fun sync_stopsAtTheLatestKnownLink_evenWhenAnEarlierEntryWasSkipped() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/new", title = "New", desc = "ok"),
            item("https://example.test/bad"),
            item("https://example.test/known", title = "Known", desc = "ok"),
            item("https://example.test/older", title = "Older", desc = "ok"),
        ))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), "https://example.test/known")
        assertEquals(listOf("New"), list.map { it.title })
    }

    @Test fun sync_aFeedThatCannotBeRead_stillGivesAnEmptyList() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), null)
        assertEquals(emptyList<Any>(), list)
    }

    // ---- logging must not leak
    // The pre-existing per-entry line ("RSS article ... entryUrl=...") already logs a redacted URL path and is not part
    // of this task, so these tests pin only what the post body and the NEW skip line may contain.
    @Test fun theLog_neverContainsThePostText() = runBlocking {
        enqueue(feedOf(item("https://example.test/@SecretAccount/1", desc = "&lt;p&gt;PRIVATE_POST_TEXT_12345&lt;/p&gt;")))
        helper.searchFeed(url())
        assertFalse("post text leaked into the log", logText().contains("PRIVATE_POST_TEXT_12345"))
    }

    @Test fun sync_theSkipLineForABadEntry_exists_andNamesNeitherTheUrlNorTheAccount() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/@SecretAccount/1", title = "Ok", desc = "ok"),
            item("https://example.test/@SecretAccount/2"),                       // no title and no body
        ))
        helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), null)
        val skipLines = ShadowLog.getLogs().map { it.msg }.filter { it.startsWith("Skip malformed RSS entry errorType=") }
        assertEquals("exactly one skip line, for the one bad entry", 1, skipLines.size)
        assertFalse(skipLines.single().contains("SecretAccount"))
        assertFalse(skipLines.single().contains("example.test"))
    }
}
