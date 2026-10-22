package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.FullContentPolicy
import com.mckimquyen.reader.domain.model.feed.Feed
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.io.SyndFeedInput
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssHelperAudioTest {

    private lateinit var rssHelper: RssHelper
    private val feed = Feed(id = "f1", name = "Show", url = "https://example.com/feed", groupId = "g1", accountId = 1)

    @Before
    fun setUp() {
        rssHelper = RssHelper(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = Dispatchers.Unconfined,
            okHttpClient = OkHttpClient(),
        )
    }

    private fun entry(itemBody: String): com.rometools.rome.feed.synd.SyndEntry {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/">
            <channel><title>T</title><link>https://x</link><description>d</description>
            <item><title>Ep</title><link>https://x/e</link><description>notes</description>$itemBody</item>
            </channel></rss>"""
        return SyndFeedInput().build(StringReader(xml)).entries.first()
    }

    @Test fun audioItem_storesThreeFields() {
        val article = rssHelper.article(feed, 1, entry("""<enclosure url="https://cdn.example.com/ep1.mp3" type="audio/mpeg" length="100"/><itunes:duration>61</itunes:duration>"""))
        assertEquals("https://cdn.example.com/ep1.mp3", article.audioUrl)
        assertEquals("audio/mpeg", article.audioMime)
        assertEquals(61, article.audioDurationSec)
    }

    @Test fun textItem_leavesAudioNull() {
        val article = rssHelper.article(feed, 1, entry(""))
        assertNull(article.audioUrl)
        assertNull(article.audioMime)
        assertNull(article.audioDurationSec)
    }

    @Test fun imageEnclosure_keepsThumbnailAndNoAudio() {
        val article = rssHelper.article(feed, 1, entry("""<enclosure url="https://cdn.example.com/pic.jpg" type="image/jpeg" length="1"/>"""))
        assertEquals("https://cdn.example.com/pic.jpg", article.img)
        assertNull(article.audioUrl)
    }

    @Test fun articleAcceptsEncodedBodyAtLimitAndPreservesMetadata() {
        val body = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES)
        val article = rssHelper.article(feed, 1, entry("""<content:encoded><![CDATA[$body]]></content:encoded>"""))
        assertEquals(body, article.fullContent)
        assertTrue(article.rawDescription.isNotBlank())
        assertTrue(article.shortDescription.isNotBlank())
        assertEquals("Ep", article.title)
        assertEquals("https://x/e", article.link)
    }

    @Test fun articleLogsOnlySizesAndRedactsSensitiveUrlQuery() {
        val descriptionSentinel = "DESCRIPTION_PRIVATE_SENTINEL"
        val contentSentinel = "CONTENT_PRIVATE_SENTINEL"
        val directEntry = SyndEntryImpl().apply {
            title = "Safe title"
            link = "https://example.test/feed/item?token=SECRET_TOKEN"
            description = SyndContentImpl().apply { type = "text/plain"; value = descriptionSentinel }
            contents = listOf(SyndContentImpl().apply { type = "text/html"; value = contentSentinel })
        }
        ShadowLog.clear()
        rssHelper.article(feed, 1, directEntry)
        val logs = ShadowLog.getLogs().joinToString("\\n") { it.msg }
        assertTrue(logs.contains("source=content"))
        assertTrue(logs.contains("descriptionBytes="))
        assertTrue(logs.contains("entryUrl=https://example.test/feed/item?<redacted>"))
        assertTrue(!logs.contains(descriptionSentinel))
        assertTrue(!logs.contains(contentSentinel))
        assertTrue(!logs.contains("SECRET_TOKEN"))

        directEntry.link = "https://PRIVATE_USER:PRIVATE_PASSWORD@example.test/feed/item?token=SECRET_TOKEN#PRIVATE_FRAGMENT"
        ShadowLog.clear()
        rssHelper.article(feed, 1, directEntry)
        val userInfoLogs = ShadowLog.getLogs().joinToString("\n") { it.msg }
        assertTrue(userInfoLogs.contains("entryUrl=https://example.test/feed/item"))
        for (secret in listOf("PRIVATE_USER", "PRIVATE_PASSWORD", "SECRET_TOKEN", "PRIVATE_FRAGMENT", descriptionSentinel, contentSentinel)) {
            assertTrue(!userInfoLogs.contains(secret))
        }
    }

    @Test fun articleRejectsOverLimitEncodedBodyAndLogsNoPayload() {
        val sentinel = "RSS_BODY_SECRET_SENTINEL"
        val body = sentinel + "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1 - sentinel.length)
        val directEntry = SyndEntryImpl().apply {
            title = "Oversized fixture"
            link = "https://example.test/oversized"
            description = SyndContentImpl().apply { type = "text/plain"; value = "Safe short description" }
            contents = listOf(SyndContentImpl().apply { type = "text/html"; value = body })
        }
        ShadowLog.clear()
        val article = rssHelper.article(feed, 1, directEntry)
        assertNull(article.fullContent)
        assertEquals("Oversized fixture", article.title)
        assertEquals("https://example.test/oversized", article.link)
        assertTrue(article.rawDescription.isNotBlank())
        val logs = ShadowLog.getLogs().joinToString("\n") { it.msg }
        assertTrue(logs.contains("source=RSS"))
        assertTrue(logs.contains("bytes=${FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1}"))
        assertTrue(!logs.contains(sentinel))
    }
}
