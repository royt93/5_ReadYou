package com.mckimquyen.reader.infrastructure.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PodcastServiceManifestTest {

    private val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    private fun service(name: String): Element? {
        val nodes = manifest.getElementsByTagName("service")
        return (0 until nodes.length).map { nodes.item(it) as Element }.firstOrNull { it.getAttribute("android:name") == name }
    }

    @Test fun podcastService_isPrivateMediaPlayback() {
        val s = service(".infrastructure.podcast.PodcastPlaybackService")
        assertNotNull("manifest phải khai báo PodcastPlaybackService", s)
        assertEquals("false", s!!.getAttribute("android:exported"))
        assertEquals("mediaPlayback", s.getAttribute("android:foregroundServiceType"))
    }

    @Test fun ttsService_unchanged() {
        val s = service(".infrastructure.audio.TtsForegroundService")
        assertNotNull(s)
        assertEquals("mediaPlayback", s!!.getAttribute("android:foregroundServiceType"))
    }
}
