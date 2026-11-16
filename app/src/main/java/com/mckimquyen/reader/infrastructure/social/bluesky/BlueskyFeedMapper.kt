package com.mckimquyen.reader.infrastructure.social.bluesky

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.rss.RssDescriptionParser
import com.mckimquyen.reader.ui.ext.spacerDollar
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Turns the public Bluesky API answer into articles. Everything in it is written by strangers: text is escaped before it is
 * stored as HTML, nothing is downloaded, and only the account's own posts are kept (no reposts, no replies).
 */
object BlueskyFeedMapper {
    const val FEED_URL_PREFIX = "bsky:"
    const val FEED_NAME_PREFIX = "Bluesky · "
    const val MAX_NAME_CODE_POINTS = 40
    const val MAX_POSTS_KEPT = 20
    private const val POST_LINK_BASE = "https://bsky.app/profile/"
    private const val HTTPS = "https"
    private const val DID_PREFIX = "did:"
    private const val BSKY_APP = "bsky.app"
    private const val BSKY_SOCIAL = "bsky.social"
    private const val DEFAULT_PORT = -1
    private const val EMBED_IMAGES = "app.bsky.embed.images#view"
    private const val EMBED_VIDEO = "app.bsky.embed.video#view"
    private const val EMBED_RECORD = "app.bsky.embed.record#view"
    private const val EMBED_RECORD_WITH_MEDIA = "app.bsky.embed.recordWithMedia#view"
    private const val EMBED_EXTERNAL = "app.bsky.embed.external#view"
    private const val RSS_PROFILE_PREFIX = "https://bsky.app/profile/"
    private const val RSS_SUFFIX = "/rss"
    // A handle or a DID starts with a letter or a digit, so ".." and ".x" are neither. The one place that decides it.
    private val ACTOR = Regex("^[A-Za-z0-9][A-Za-z0-9:._-]{0,252}$")

    /** True for a handle or a DID as the API takes it: safe to put in a query, nothing else. */
    fun isActor(text: String): Boolean = ACTOR.matches(text)

    /** The handle or DID inside the RSS address the resolver builds for a Bluesky account; null for any other address. */
    fun actorFromRssUrl(rssUrl: String?): String? {
        if (rssUrl == null || !rssUrl.startsWith(RSS_PROFILE_PREFIX) || !rssUrl.endsWith(RSS_SUFFIX)) return null
        val actor = rssUrl.removePrefix(RSS_PROFILE_PREFIX).removeSuffix(RSS_SUFFIX)
        return actor.takeIf { isActor(it) }
    }

    /** A handle is case-insensitive, a DID is not, so only a handle is lower-cased. */
    fun feedUrl(actor: String): String {
        val trimmed = actor.trim()
        return FEED_URL_PREFIX + if (trimmed.startsWith(DID_PREFIX)) trimmed else trimmed.lowercase(Locale.ROOT)
    }

    fun feedName(actor: String, displayName: String?): String =
        FEED_NAME_PREFIX + (displayName?.takeIf { it.isNotEmpty() } ?: "@" + actor.trim().lowercase(Locale.ROOT))

    /** Same cleaning as a Nostr profile name: control characters and spaces become one space, at most 40 code points. */
    fun cleanDisplayName(raw: String?): String? {
        if (raw == null) return null
        val spaced = buildString(raw.length) {
            for (c in raw) {
                val blank = Character.getType(c) == Character.CONTROL.toInt() || Character.isSpaceChar(c)
                append(if (blank) ' ' else c)
            }
        }
        val cleaned = spaced.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        if (cleaned.isEmpty()) return null
        if (cleaned.codePointCount(0, cleaned.length) <= MAX_NAME_CODE_POINTS) return cleaned
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_CODE_POINTS)).trimEnd()
    }

    /** The avatar address, only when it is plain https on a Bluesky host (the address is chosen by the account owner). */
    fun iconUrl(avatar: String?): String? {
        if (avatar.isNullOrEmpty()) return null
        val uri = runCatching { URI(avatar) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val onBluesky = host == BSKY_APP || host.endsWith(".$BSKY_APP") || host == BSKY_SOCIAL || host.endsWith(".$BSKY_SOCIAL")
        val plain = uri.scheme == HTTPS && uri.userInfo == null && uri.port == DEFAULT_PORT
        return avatar.takeIf { onBluesky && plain }
    }

    fun toArticles(
        profileDid: String,
        items: JSONArray,
        feedId: String,
        accountId: Int,
        nowMillis: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<Article> {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<Long, Article>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.has("reason")) continue
            val post = item.optJSONObject("post") ?: continue
            val uri = post.optString("uri")
            val rkey = uri.substringAfterLast('/', "")
            if (rkey.isEmpty() || !seen.add(rkey)) continue
            if (post.optJSONObject("author")?.optString("did") != profileDid) continue
            val record = post.optJSONObject("record") ?: continue
            if (record.has("reply")) continue
            val text = record.optString("text")
            if (text.isBlank()) continue
            val html = escapeToHtml(text) + embedLine(post.optJSONObject("embed"))
            val title = RssDescriptionParser.titleFromBody(html)
            if (title.isEmpty()) continue
            val millis = minOf(parseMillis(record.optString("createdAt"), nowMillis), nowMillis)
            out += millis to Article(
                id = accountId.spacerDollar(newId()),
                date = Date(millis),
                title = title,
                rawDescription = html,
                shortDescription = title.take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH),
                link = "$POST_LINK_BASE$profileDid/post/$rkey",
                feedId = feedId,
                accountId = accountId,
            )
        }
        return out.sortedByDescending { it.first }.take(MAX_POSTS_KEPT).map { it.second }
    }

    private fun parseMillis(text: String, fallback: Long): Long =
        runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(fallback)

    /** One short line, never an image address: nothing in a post is downloaded. */
    private fun embedLine(embed: JSONObject?): String {
        if (embed == null) return ""
        return when (embed.optString("\$type")) {
            EMBED_IMAGES -> "<br>[image]"
            EMBED_VIDEO -> "<br>[video]"
            EMBED_RECORD, EMBED_RECORD_WITH_MEDIA -> "<br>[quoted post]"
            EMBED_EXTERNAL -> {
                val link = embed.optJSONObject("external")?.optString("uri").orEmpty()
                if (link.startsWith("$HTTPS://")) "<br>[link] " + escapeToHtml(link) else "<br>[link]"
            }
            else -> ""
        }
    }

    private fun escapeToHtml(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                '\n' -> append("<br>")
                '\r' -> Unit
                else -> append(c)
            }
        }
    }
}
