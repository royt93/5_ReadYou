package com.mckimquyen.reader.domain.security

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object AntiTrackerHtmlSanitizer {

    private val TRACKING_URL_PATTERNS = listOf(
        "/pixel",
        "/track/",
        "/tr?",
        "1x1",
        "doubleclick.net",
        "google-analytics.com",
        "facebook.com/tr",
        "scorecardresearch.com",
        "pixel.wp.com",
        "mailtrack",
        "open.substack.com",
        "email.ghost.org",
        "beehiiv.com/track",
    )

    private val ALLOWED_VIDEO_HOSTS = listOf(
        "youtube.com",
        "youtube-nocookie.com",
        "youtu.be",
        "vimeo.com",
        "player.vimeo.com",
    )

    private const val MAX_TINY_PIXEL_DIMENSION = 2

    fun sanitize(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val doc: Document = Jsoup.parseBodyFragment(html)
        val body = doc.body()

        // 1. Remove tracking pixels & hidden images
        val images = body.select("img")
        for (img in images) {
            val src = img.attr("src").lowercase()
            val width = img.attr("width").toIntOrNull()
            val height = img.attr("height").toIntOrNull()
            val style = img.attr("style").lowercase()

            val isTiny = (width != null && width <= MAX_TINY_PIXEL_DIMENSION) ||
                    (height != null && height <= MAX_TINY_PIXEL_DIMENSION)
            val isHidden = style.contains("display:none") || style.contains("display: none") ||
                    style.contains("visibility:hidden") || style.contains("visibility: hidden")
            val isTrackerUrl = TRACKING_URL_PATTERNS.any { src.contains(it) }

            if (isTiny || isHidden || isTrackerUrl) {
                img.remove()
            }
        }

        // 2. Remove all script tags
        body.select("script").remove()

        // 3. Filter iframes (keep only legitimate video hosts)
        val iframes = body.select("iframe")
        for (iframe in iframes) {
            val src = iframe.attr("src").lowercase()
            val isAllowedVideo = ALLOWED_VIDEO_HOSTS.any { src.contains(it) }
            if (!isAllowedVideo) {
                iframe.remove()
            }
        }

        return body.html().trim()
    }
}
