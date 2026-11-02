package com.mckimquyen.reader.domain.curated

import java.net.URI

/** Which sources of a bundle still need subscribing and which the account already has. */
data class BundleSubscribePlan(
    val toAdd: List<CuratedSource>,
    val alreadyHave: List<CuratedSource>,
)

object BundleSubscribePlanner {

    /**
     * Splits [sources] into those to subscribe and those the account already has. A URL repeated inside the bundle is
     * kept once (first wins). A URL that is not a valid absolute URL is never subscribed: it goes to `alreadyHave` so
     * the caller neither fetches it nor counts it as a failure it caused.
     */
    fun plan(sources: List<CuratedSource>, existingUrls: Set<String>): BundleSubscribePlan {
        val existing = existingUrls.mapTo(HashSet()) { normalizeUrl(it) }
        val seen = HashSet<String>()
        val toAdd = mutableListOf<CuratedSource>()
        val alreadyHave = mutableListOf<CuratedSource>()
        for (source in sources) {
            val key = normalizeUrl(source.url)
            when {
                !isAbsoluteUrl(key) || key in existing -> alreadyHave += source
                seen.add(key) -> toAdd += source
            }
        }
        return BundleSubscribePlan(toAdd = toAdd, alreadyHave = alreadyHave)
    }

    private fun isAbsoluteUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        !uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

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
