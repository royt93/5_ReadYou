package com.mckimquyen.reader.domain.curated

import java.net.URI

/** Which sources of a bundle still need subscribing and which the account already has. */
data class BundleSubscribePlan(
    val toAdd: List<CuratedSource>,
    val alreadyHave: List<CuratedSource>,
)

object BundleSubscribePlanner {

    /**
     * Compares URLs the way a person would: scheme and host are case-insensitive and one trailing slash on the path is
     * ignored. Path case, query and fragment are kept as written because servers treat them as significant. A URL that
     * cannot be parsed is returned trimmed so callers can still compare it as plain text.
     */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val scheme = uri.scheme?.lowercase() ?: return trimmed
        val host = uri.host?.lowercase() ?: return trimmed
        val path = uri.rawPath.orEmpty().removeSuffix("/")
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "$scheme://$host$port$path$query$fragment"
    }
}
