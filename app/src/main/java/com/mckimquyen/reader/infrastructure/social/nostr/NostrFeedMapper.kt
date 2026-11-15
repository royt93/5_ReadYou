package com.mckimquyen.reader.infrastructure.social.nostr

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.rss.RssDescriptionParser
import com.mckimquyen.reader.ui.ext.spacerDollar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Turns verified Nostr notes into articles. The note text is untrusted: it is escaped before it is stored as HTML. */
object NostrFeedMapper {
    const val NJUMP_BASE = "https://njump.me/"
    const val FEED_URL_PREFIX = "nostr:"
    const val FEED_NAME_PREFIX = "Nostr · "
    private const val MILLIS_PER_SECOND = 1000L
    private const val NAME_HEAD_CHARS = 8
    private const val NAME_TAIL_CHARS = 4
    private const val ELLIPSIS = "…"

    fun feedName(npub: String): String =
        FEED_NAME_PREFIX + npub.take(NAME_HEAD_CHARS) + ELLIPSIS + npub.takeLast(NAME_TAIL_CHARS)

    /**
     * The profile name the author gave themselves, behind the same prefix; without one, the short npub name. A blank name
     * never reaches here: [NostrProfileName.fromContent] returns null instead of an empty string.
     */
    fun feedName(npub: String, profileName: String?): String =
        if (profileName == null) feedName(npub) else FEED_NAME_PREFIX + profileName

    fun feedUrl(npub: String): String = FEED_URL_PREFIX + npub.trim().lowercase(Locale.ROOT)

    fun toArticles(
        events: List<NostrEvent>,
        feedId: String,
        accountId: Int,
        nowMillis: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<Article> =
        events.asSequence()
            .filterNot { it.isReply }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(NostrRelayClient.MAX_NOTES_KEPT)
            .mapNotNull { event ->
                val noteId = NostrKeys.noteId(event.id) ?: return@mapNotNull null
                val html = escapeToHtml(event.content)
                val title = RssDescriptionParser.titleFromBody(html)
                if (title.isEmpty()) return@mapNotNull null
                Article(
                    id = accountId.spacerDollar(newId()),
                    date = Date(minOf(event.createdAt * MILLIS_PER_SECOND, nowMillis)),
                    title = title,
                    rawDescription = html,
                    shortDescription = title.take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH),
                    link = NJUMP_BASE + noteId,
                    feedId = feedId,
                    accountId = accountId,
                )
            }
            .toList()

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
