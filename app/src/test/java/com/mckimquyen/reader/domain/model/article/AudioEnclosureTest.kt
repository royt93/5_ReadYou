package com.mckimquyen.reader.domain.model.article

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader

class AudioEnclosureTest {

    private fun entry(itemBody: String): SyndEntry {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
            <channel><title>T</title><link>https://x</link><description>d</description>
            <item><title>E</title><link>https://x/e</link>$itemBody</item>
            </channel></rss>"""
        return SyndFeedInput().build(StringReader(xml)).entries.first()
    }

    @Test fun mp3WithDuration() {
        val e = entry("""<enclosure url="https://x/e.mp3" type="audio/mpeg" length="1"/><itunes:duration>1913</itunes:duration>""")
        assertEquals(AudioInfo("https://x/e.mp3", "audio/mpeg", 1913), AudioEnclosure.from(e))
    }

    @Test fun missingTypeUsesExtension() {
        val e = entry("""<enclosure url="https://x/e.m4a" length="1"/>""")
        assertEquals(AudioInfo("https://x/e.m4a", null, null), AudioEnclosure.from(e))
    }

    @Test fun imageOnlyIsNull() {
        assertNull(AudioEnclosure.from(entry("""<enclosure url="https://x/a.jpg" type="image/jpeg" length="1"/>""")))
    }

    @Test fun httpIsAccepted_andUpgradedAtPlaybackTime() {
        assertEquals("http://x/e.mp3", AudioEnclosure.from(entry("""<enclosure url="http://x/e.mp3" type="audio/mpeg" length="1"/>"""))?.url)
    }

    @Test fun otherSchemesAreRejected() {
        assertNull(AudioEnclosure.from(entry("""<enclosure url="ftp://x/e.mp3" type="audio/mpeg" length="1"/>""")))
        assertNull(AudioEnclosure.from(entry("""<enclosure url="file:///sdcard/e.mp3" type="audio/mpeg" length="1"/>""")))
        assertNull(AudioEnclosure.from(entry("""<enclosure url="javascript:alert(1)" type="audio/mpeg" length="1"/>""")))
    }

    @Test fun audioPickedAfterImage() {
        val e = entry(
            """<enclosure url="https://x/a.jpg" type="image/jpeg" length="1"/>
               <enclosure url="https://x/e.mp3" type="audio/mpeg" length="1"/>""",
        )
        assertEquals("https://x/e.mp3", AudioEnclosure.from(e)?.url)
    }

    @Test fun noEnclosure() = assertNull(AudioEnclosure.from(entry("")))

    @Test fun durationMinutesSeconds() {
        val e = entry("""<enclosure url="https://x/e.mp3" type="audio/mpeg" length="1"/><itunes:duration>12:34</itunes:duration>""")
        assertEquals(754, AudioEnclosure.from(e)?.durationSec)
    }

    @Test fun uppercaseTypeAccepted() {
        assertEquals("https://x/e.mp3", AudioEnclosure.from(entry("""<enclosure url="https://x/e.mp3" type="AUDIO/MPEG" length="1"/>"""))?.url)
    }
}
