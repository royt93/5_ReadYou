package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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

    private fun entry(itemBody: String): SyndEntry {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
            <channel><title>T</title><link>https://x</link><description>d</description>
            <item><title>Ep</title><link>https://x/e</link><description>notes</description>$itemBody</item>
            </channel></rss>"""
        return SyndFeedInput().build(StringReader(xml)).entries.first()
    }

    @Test
    fun audioItem_storesThreeFields() {
        val article = rssHelper.article(
            feed, 1,
            entry("""<enclosure url="https://cdn.example.com/ep1.mp3" type="audio/mpeg" length="100"/><itunes:duration>61</itunes:duration>"""),
        )
        assertEquals("https://cdn.example.com/ep1.mp3", article.audioUrl)
        assertEquals("audio/mpeg", article.audioMime)
        assertEquals(61, article.audioDurationSec)
    }

    @Test
    fun textItem_leavesAudioNull() {
        val article = rssHelper.article(feed, 1, entry(""))
        assertNull(article.audioUrl)
        assertNull(article.audioMime)
        assertNull(article.audioDurationSec)
    }

    @Test
    fun imageEnclosure_keepsThumbnailAndNoAudio() {
        val article = rssHelper.article(
            feed, 1,
            entry("""<enclosure url="https://cdn.example.com/pic.jpg" type="image/jpeg" length="1"/>"""),
        )
        assertEquals("https://cdn.example.com/pic.jpg", article.img)
        assertNull(article.audioUrl)
    }
}
