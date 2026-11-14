package com.mckimquyen.reader.infrastructure.social

import java.net.URI

/**
 * Turns what a person pastes ("@name@server", "@name.bsky.social", or a profile link) into the public RSS address of a
 * Mastodon or Bluesky account (FRONT-02). Pure: no Android, no network. Anything it does not recognise returns null so
 * the existing add-a-source flow keeps handling it, and it only ever builds https addresses.
 */
object SocialFeedUrlResolver {
    private const val BLUESKY_HOST = "bsky.app"
    private const val MAX_DOMAIN_LENGTH = 253
    private const val MIN_DOMAIN_LABELS = 2
    private const val HTTPS = "https"
    private const val DEFAULT_PORT = -1

    /** Sites that also use "/@name" paths but are not Mastodon; they keep their own handling. */
    private val NOT_MASTODON_HOSTS = listOf(
        "youtube.com", "youtu.be", "medium.com", "substack.com", "tiktok.com",
        "instagram.com", "x.com", "twitter.com", "threads.net", "bsky.app",
    )
    private val NAME = Regex("^[A-Za-z0-9_.-]+$")
    private val LABEL = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$")
    private val DID = Regex("^did:(?:plc|web):[A-Za-z0-9._:-]+$")
    private val FEED_SUFFIXES = listOf(".rss", ".atom")

    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any(Char::isWhitespace)) return null
        return when {
            text.startsWith("@") -> fromHandle(text)
            text.startsWith("$HTTPS://") -> fromLink(text)
            else -> null
        }
    }

    private fun fromHandle(text: String): String? {
        val parts = text.removePrefix("@").split("@")
        return when (parts.size) {
            2 -> {
                val (name, server) = parts
                if (isName(name) && isDomain(server) && !isExcluded(server.lowercase())) {
                    "$HTTPS://${server.lowercase()}/@$name.rss"
                } else {
                    null
                }
            }
            1 -> parts[0].takeIf(::isDomain)?.let { "$HTTPS://$BLUESKY_HOST/profile/${it.lowercase()}/rss" }
            else -> null
        }
    }

    private fun fromLink(text: String): String? {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (uri.userInfo != null || uri.port != DEFAULT_PORT) return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.rawPath.orEmpty().split("/").filter { it.isNotEmpty() }
        return if (host == BLUESKY_HOST) blueskyLink(segments) else mastodonLink(host, segments)
    }

    private fun blueskyLink(segments: List<String>): String? {
        val isProfile = segments.size == 2 || (segments.size == 3 && segments[2] == "rss")
        if (!isProfile || segments[0] != "profile") return null
        val id = segments[1]
        return when {
            // A DID is an identifier whose case matters, so it is kept exactly as given.
            DID.matches(id) -> "$HTTPS://$BLUESKY_HOST/profile/$id/rss"
            isDomain(id) -> "$HTTPS://$BLUESKY_HOST/profile/${id.lowercase()}/rss"
            else -> null
        }
    }

    private fun mastodonLink(host: String, segments: List<String>): String? {
        if (isExcluded(host) || !isDomain(host) || segments.size != 1) return null
        val first = segments[0]
        if (!first.startsWith("@")) return null
        val name = first.removePrefix("@")
        return if (isName(name)) "$HTTPS://$host/@$name.rss" else null
    }

    private fun isName(name: String): Boolean =
        NAME.matches(name) && FEED_SUFFIXES.none { name.endsWith(it, ignoreCase = true) }

    private fun isDomain(value: String): Boolean {
        if (value.length > MAX_DOMAIN_LENGTH) return false
        val labels = value.split(".")
        return labels.size >= MIN_DOMAIN_LABELS && labels.all(LABEL::matches)
    }

    private fun isExcluded(host: String): Boolean = NOT_MASTODON_HOSTS.any { host == it || host.endsWith(".$it") }
}
