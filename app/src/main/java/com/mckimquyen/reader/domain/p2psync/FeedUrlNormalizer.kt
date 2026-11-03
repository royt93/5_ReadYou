package com.mckimquyen.reader.domain.p2psync

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object FeedUrlNormalizer {
    fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > SyncLimits.MAX_FEED_URL_CHARS) return null
        if (value.any { it.isWhitespace() || it.isISOControl() }) return null
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            if (scheme != "http" && scheme != "https") return null
            if (uri.rawUserInfo != null) return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (host.isEmpty()) return null
            val port = when {
                scheme == "http" && uri.port == 80 -> -1
                scheme == "https" && uri.port == 443 -> -1
                else -> uri.port
            }
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }.let {
                if (it.length > 1 && it.endsWith('/')) it.dropLast(1) else it
            }
            URI(scheme, null, host, port, path, uri.rawQuery, null).toASCIIString()
                .takeIf { it.length <= SyncLimits.MAX_FEED_URL_CHARS }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
