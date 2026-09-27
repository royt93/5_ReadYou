package com.mckimquyen.reader.infrastructure.media.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleVideoExtractorTest {

    @Test
    fun `extracts direct src from a video tag`() {
        val videos = ArticleVideoExtractor.extract(
            """<p>Intro</p><video src="https://cdn.example.com/clip.mp4"></video>"""
        )

        assertEquals(1, videos.size)
        assertEquals("https://cdn.example.com/clip.mp4", videos.first().url)
        assertNull("No poster attribute means no poster", videos.first().posterUrl)
    }

    @Test
    fun `extracts nested source children and keeps the poster`() {
        val videos = ArticleVideoExtractor.extract(
            """
            <video poster="https://cdn.example.com/thumb.jpg">
              <source src="https://cdn.example.com/clip.webm" type="video/webm">
              <source src="https://cdn.example.com/clip.mp4" type="video/mp4">
            </video>
            """.trimIndent()
        )

        assertEquals(2, videos.size)
        assertEquals(
            listOf("https://cdn.example.com/clip.webm", "https://cdn.example.com/clip.mp4"),
            videos.map { it.url },
        )
        assertTrue(videos.all { it.posterUrl == "https://cdn.example.com/thumb.jpg" })
    }

    @Test
    fun `resolves root-relative src against the article url`() {
        val videos = ArticleVideoExtractor.extract(
            """<video src="/media/clip.mp4"></video>""",
            baseUrl = "https://news.example.com/story/1",
        )

        assertEquals(listOf("https://news.example.com/media/clip.mp4"), videos.map { it.url })
    }

    @Test
    fun `deduplicates a url repeated across src and source`() {
        val videos = ArticleVideoExtractor.extract(
            """
            <video src="https://cdn.example.com/clip.mp4">
              <source src="https://cdn.example.com/clip.mp4" type="video/mp4">
            </video>
            """.trimIndent()
        )

        assertEquals(1, videos.size)
    }

    @Test
    fun `accepts hls and dash streams`() {
        val videos = ArticleVideoExtractor.extract(
            """
            <video><source src="https://cdn.example.com/live.m3u8" type="application/x-mpegURL"></video>
            <video><source src="https://cdn.example.com/live.mpd" type="application/dash+xml"></video>
            """.trimIndent()
        )

        assertEquals(2, videos.size)
    }

    @Test
    fun `ignores youtube iframes so their own terms keep applying`() {
        val videos = ArticleVideoExtractor.extract(
            """<iframe src="https://www.youtube.com/embed/abc123"></iframe>"""
        )

        assertTrue("iframe embeds must never reach the native player", videos.isEmpty())
    }

    @Test
    fun `ignores non http schemes and unknown extensions`() {
        val videos = ArticleVideoExtractor.extract(
            """
            <video src="blob:https://example.com/abc"></video>
            <video src="file:///sdcard/clip.mp4"></video>
            <video src="https://cdn.example.com/page.html"></video>
            <video></video>
            """.trimIndent()
        )

        assertTrue(videos.isEmpty())
    }

    @Test
    fun `returns empty for blank and malformed html`() {
        assertTrue(ArticleVideoExtractor.extract(null).isEmpty())
        assertTrue(ArticleVideoExtractor.extract("").isEmpty())
        assertTrue(ArticleVideoExtractor.extract("   ").isEmpty())
        assertTrue(ArticleVideoExtractor.extract("<video><<<>>> broken").isEmpty())
    }

    @Test
    fun `isPlayable ignores query strings and fragments when reading the extension`() {
        assertTrue(ArticleVideoExtractor.isPlayable("https://a.example/c.mp4?token=1&x=2"))
        assertTrue(ArticleVideoExtractor.isPlayable("https://a.example/c.mp4#t=30"))
        assertFalse(ArticleVideoExtractor.isPlayable("https://a.example/c?mp4"))
    }

    @Test
    fun `isPlayable trusts a declared video mime type over the extension`() {
        // Signed CDN URLs often carry no extension at all.
        assertTrue(ArticleVideoExtractor.isPlayable("https://a.example/stream", "video/mp4"))
        assertTrue(
            "codec parameters must not defeat the match",
            ArticleVideoExtractor.isPlayable("https://a.example/s", "application/x-mpegURL; codecs=\"avc1\""),
        )
        assertFalse(ArticleVideoExtractor.isPlayable("https://a.example/s", "text/html"))
    }

    @Test
    fun `isPlayable rejects blank input`() {
        assertFalse(ArticleVideoExtractor.isPlayable(null))
        assertFalse(ArticleVideoExtractor.isPlayable(""))
        assertFalse(ArticleVideoExtractor.isPlayable("   ", "video/mp4"))
    }
}
