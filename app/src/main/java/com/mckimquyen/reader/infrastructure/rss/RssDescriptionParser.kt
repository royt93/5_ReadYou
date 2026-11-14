package com.mckimquyen.reader.infrastructure.rss

import android.os.Build
import android.text.Html
import net.dankito.readability4j.extended.Readability4JExtended

/** Parses an RSS summary defensively so one malformed entry cannot abort feed import/sync. */
internal object RssDescriptionParser {
    const val MAX_SHORT_DESCRIPTION_LENGTH = 110

    fun shortDescription(html: String?): String {
        val source = html.orEmpty()
        if (source.isBlank()) return ""
        val readableText = runCatching {
            Readability4JExtended(EMPTY_BASE_URL, source).parse().textContent
        }.getOrNull()
        val text = readableText?.takeIf(String::isNotBlank)
            ?: runCatching { plainText(source) }.getOrNull().orEmpty()
        return text.take(MAX_SHORT_DESCRIPTION_LENGTH).trim()
    }

    const val FALLBACK_TITLE_MAX_LENGTH = 80
    const val ELLIPSIS = "…"

    /**
     * A title for an entry that has none (Mastodon and Bluesky posts never do): the first words of the body as plain
     * text. Counted in Unicode code points so an emoji at the cut is kept or dropped whole; empty when there is no text.
     */
    fun titleFromBody(html: String?, maxLength: Int = FALLBACK_TITLE_MAX_LENGTH): String {
        if (maxLength <= 0) return ""
        val text = runCatching { plainText(html.orEmpty()) }.getOrNull().orEmpty()
        // An empty text (no body, or only tags and spaces) is already shorter than any limit, so it comes back empty.
        if (text.codePointCount(0, text.length) <= maxLength) return text
        val end = text.offsetByCodePoints(0, maxLength)
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }

    private fun plainText(html: String): String {
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
        return decoded.toString().replace(WHITESPACE_REGEX, " ").trim()
    }

    private const val EMPTY_BASE_URL = "https://rss.invalid/"
    private val WHITESPACE_REGEX = Regex("\\s+")
}
