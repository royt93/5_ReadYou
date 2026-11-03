package com.mckimquyen.reader.domain.p2psync

import com.mckimquyen.reader.domain.mirror.LanAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

data class SyncSessionUrl(val host: String, val port: Int, val token: String) {
    val path: String get() = "/s/$token"

    companion object {
        fun parse(raw: String, isAllowedHost: (String) -> Boolean = LanAddress::isPrivateIpv4): SyncSessionUrl? {
            if (raw.length > MAX_URL_CHARS || raw.any { it.isWhitespace() || it.isISOControl() }) return null
            return try {
                val uri = URI(raw)
                val host = uri.host?.lowercase(Locale.ROOT) ?: return null
                if (!uri.scheme.equals("http", ignoreCase = true)) return null
                if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
                if (!isAllowedHost(host)) return null
                if (!Regex("^/s/[0-9a-f]{32}$").matches(uri.rawPath.orEmpty())) return null
                if (uri.port !in 1..65535) return null
                SyncSessionUrl(host, uri.port, uri.rawPath.removePrefix("/s/"))
            } catch (_: URISyntaxException) {
                null
            }
        }

        private const val MAX_URL_CHARS = 512
    }
}
