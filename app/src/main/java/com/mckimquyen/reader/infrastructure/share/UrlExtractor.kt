package com.mckimquyen.reader.infrastructure.share

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.regex.Pattern

object UrlExtractor {
    private val URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+")

    fun extractValidUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val raw = matcher.group().trimEnd('.', ',', ';', '!', '?')
            val parsed = raw.toHttpUrlOrNull()
            if (parsed != null && (parsed.scheme == "http" || parsed.scheme == "https")) {
                return raw
            }
        }
        return null
    }
}
