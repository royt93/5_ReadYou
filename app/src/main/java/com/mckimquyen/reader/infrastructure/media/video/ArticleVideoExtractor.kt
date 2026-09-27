package com.mckimquyen.reader.infrastructure.media.video

import org.jsoup.Jsoup

/**
 * Pulls directly playable video sources out of article HTML.
 *
 * Only progressive/streaming URLs that ExoPlayer can open on its own are returned. Platform
 * embeds (YouTube, Vimeo, …) are deliberately ignored: those arrive as `<iframe>` and keep their
 * existing "tap to open externally" handling in `HtmlToComposable`, which is also what their terms
 * of service require.
 */
object ArticleVideoExtractor {

    /** Containers ExoPlayer plays without an extra extension. */
    private val SUPPORTED_EXTENSIONS = setOf("mp4", "webm", "m3u8", "mpd", "mkv", "3gp")

    private const val MIME_VIDEO_PREFIX = "video/"
    private const val MIME_HLS = "application/x-mpegurl"
    private const val MIME_HLS_VND = "application/vnd.apple.mpegurl"
    private const val MIME_DASH = "application/dash+xml"

    private val SUPPORTED_MIME_TYPES = setOf(MIME_HLS, MIME_HLS_VND, MIME_DASH)

    /**
     * @param html raw article HTML (`fullContent` or `rawDescription`)
     * @param baseUrl article link, so protocol-relative and root-relative `src` resolve correctly
     * @return playable sources in document order, deduplicated, empty when there is nothing to play
     */
    fun extract(html: String?, baseUrl: String = ""): List<ArticleVideo> {
        if (html.isNullOrBlank()) return emptyList()

        val document = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return emptyList()
        val videos = LinkedHashMap<String, ArticleVideo>()

        for (element in document.select("video")) {
            val poster = element.absUrl("poster").ifBlank { null }

            // A <video> may carry src itself and/or wrap several <source> children.
            val candidates = buildList {
                add(element.absUrl("src") to element.attr("type"))
                for (source in element.select("source")) {
                    add(source.absUrl("src") to source.attr("type"))
                }
            }

            for ((url, mimeType) in candidates) {
                if (!isPlayable(url, mimeType)) continue
                videos.getOrPut(url) { ArticleVideo(url = url, posterUrl = poster) }
            }
        }

        return videos.values.toList()
    }

    /** True when [url] is an absolute http(s) address ExoPlayer can play. */
    fun isPlayable(url: String?, mimeType: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }

        val declaredType = mimeType?.trim()?.lowercase()
        if (!declaredType.isNullOrEmpty()) {
            if (declaredType.startsWith(MIME_VIDEO_PREFIX)) return true
            // Strip codec parameters, e.g. `application/x-mpegURL; codecs="avc1"`.
            if (declaredType.substringBefore(';').trim() in SUPPORTED_MIME_TYPES) return true
        }

        return extensionOf(url) in SUPPORTED_EXTENSIONS
    }

    private fun extensionOf(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/', missingDelimiterValue = path)
        if (!lastSegment.contains('.')) return null
        return lastSegment.substringAfterLast('.').lowercase().ifBlank { null }
    }
}

/** A single playable video found inside an article. */
data class ArticleVideo(
    val url: String,
    val posterUrl: String? = null,
)
