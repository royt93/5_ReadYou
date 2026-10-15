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
