package com.mckimquyen.reader.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiTrackerHtmlSanitizerTest {

    @Test
    fun sanitize_removesTinyTrackingPixelsAndHiddenImages() {
        val html = """
            <p>Welcome to our newsletter.</p>
            <img src="https://tracking.com/pixel.gif" width="1" height="1" alt="" />
            <img src="https://example.com/logo.png" width="800" height="400" />
            <img src="https://tracker.net/dot.png" style="display:none;" />
            <img src="https://stats.com/pixel?user=123" width="10" height="10" />
        """.trimIndent()

        val sanitized = AntiTrackerHtmlSanitizer.sanitize(html)
        assertTrue(sanitized.contains("logo.png"))
        assertFalse(sanitized.contains("tracking.com/pixel.gif"))
        assertFalse(sanitized.contains("display:none"))
        assertFalse(sanitized.contains("stats.com/pixel"))
    }

    @Test
    fun sanitize_removesAllScriptTags() {
        val html = """
            <article>
                <h1>Breaking Story</h1>
                <script type="text/javascript">fetch('https://evil-analytics.com');</script>
                <p>Real article paragraph content.</p>
                <script src="https://cdn.tracking.js"></script>
            </article>
        """.trimIndent()

        val sanitized = AntiTrackerHtmlSanitizer.sanitize(html)
        assertTrue(sanitized.contains("Real article paragraph content."))
        assertFalse(sanitized.contains("<script"))
        assertFalse(sanitized.contains("evil-analytics.com"))
        assertFalse(sanitized.contains("tracking.js"))
    }

    @Test
    fun sanitize_preservesAllowedVideoIframesWhileRemovingAdIframes() {
        val html = """
            <div>
                <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ" width="560" height="315"></iframe>
                <iframe src="https://player.vimeo.com/video/123456"></iframe>
                <iframe src="https://adnetwork.com/banner?id=999"></iframe>
                <iframe src="https://tracking-widget.org/embed"></iframe>
            </div>
        """.trimIndent()

        val sanitized = AntiTrackerHtmlSanitizer.sanitize(html)
        assertTrue(sanitized.contains("youtube.com/embed/dQw4w9WgXcQ"))
        assertTrue(sanitized.contains("player.vimeo.com/video/123456"))
        assertFalse(sanitized.contains("adnetwork.com"))
        assertFalse(sanitized.contains("tracking-widget.org"))
    }

    @Test
    fun sanitize_handlesBlankAndNormalTextCorrectly() {
        assertEquals("", AntiTrackerHtmlSanitizer.sanitize(null))
        assertEquals("", AntiTrackerHtmlSanitizer.sanitize("   "))
        assertEquals("<p>Simple clean paragraph.</p>", AntiTrackerHtmlSanitizer.sanitize("<p>Simple clean paragraph.</p>"))
    }
}
