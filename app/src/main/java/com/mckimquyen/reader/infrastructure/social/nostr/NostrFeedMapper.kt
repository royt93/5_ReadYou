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

    /** The most articles one Nostr source shows: the newest, replies and ordinary notes alike (see [visibleNotes]). */
    const val MAX_ARTICLES_SHOWN = 20
    /** The sign that starts the last line of a reply: it is a symbol, not a word, so it needs no translation. */
    const val REPLY_MARK = "↩"

    /** The longest cover image address kept. A longer link is skipped, so one line of a note cannot fill the database. */
    const val MAX_COVER_URL_LENGTH = 2048
    private const val COVER_IMAGE_EXTENSIONS = "jpe?g|png|gif|webp"

    // https only, up to an image extension, then an optional ?query or #fragment, then a space or the end of the text.
    private val COVER_IMAGE_URL = Regex("""https://\S+?\.(?:$COVER_IMAGE_EXTENSIONS)(?:[?#]\S*)?(?=\s|$)""", RegexOption.IGNORE_CASE)

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

    /**
     * The address of the first image link in the note text as the author typed it (not the escaped HTML): https, an image extension,
     * not longer than [MAX_COVER_URL_LENGTH]. Nothing is downloaded here; the list shows it only when the user's image switch is on.
     */
    fun coverImageUrl(content: String): String? =
        COVER_IMAGE_URL.findAll(content).map { it.value }.firstOrNull { it.length <= MAX_COVER_URL_LENGTH }

    /** The longest link made tappable. A longer one stays plain text, so one line of a note cannot fill the database. */
    const val MAX_LINK_URL_LENGTH = 2048
    private const val LINK_SCHEME = "https://"
    private const val LINK_TRAILING_PUNCTUATION = ".,;:!?)]}'\""
    private val LINK_CANDIDATE = Regex("""https://\S+""", RegexOption.IGNORE_CASE)

    /**
     * Where the https links are in the note text as the author typed it: from the scheme to the next space, without the punctuation that
     * ends a sentence, at least one character after the scheme, not longer than [MAX_LINK_URL_LENGTH]. Each range holds both ends.
     */
    fun linkSpans(content: String): List<IntRange> =
        LINK_CANDIDATE.findAll(content).mapNotNull { match ->
            val link = match.value.trimEnd { it in LINK_TRAILING_PUNCTUATION }
            if (link.length <= LINK_SCHEME.length || link.length > MAX_LINK_URL_LENGTH) null
            else match.range.first until match.range.first + link.length
        }.toList()

    fun feedUrl(npub: String): String = FEED_URL_PREFIX + npub.trim().lowercase(Locale.ROOT)

    fun toArticles(
        events: List<NostrEvent>,
        feedId: String,
        accountId: Int,
        nowMillis: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<Article> =
        visibleNotes(events).asSequence()
            .sortedByDescending { it.createdAt }
            .take(MAX_ARTICLES_SHOWN)
            .map { event ->
                // visibleNotes already refused every note without a valid id or a title, so both are there.
                val noteId = requireNotNull(NostrKeys.noteId(event.id))
                val html = escapeToHtml(event.content)
                val title = titleOf(html)
                val body = html + replyLine(event)
                Article(
                    id = accountId.spacerDollar(newId()),
                    date = Date(minOf(event.createdAt * MILLIS_PER_SECOND, nowMillis)),
                    title = title,
                    rawDescription = body,
                    shortDescription = title.take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH),
                    link = NJUMP_BASE + noteId,
                    img = coverImageUrl(event.content),
                    feedId = feedId,
                    accountId = accountId,
                )
            }
            .toList()

    /**
     * The notes that become articles, in the order they came, each once: a valid id to link to and a title that is not empty (a note of
     * nothing but spaces has none). A reply is a note like any other (it only gets a last line that links to the note it answers). This is
     * the ONE rule for "which notes are shown": [toArticles] cuts to [MAX_ARTICLES_SHOWN] after it, and the relay client asks it before
     * deciding that a relay had something to show, so a blank note never stops the search at the first relay.
     */
    fun visibleNotes(events: List<NostrEvent>): List<NostrEvent> =
        events.distinctBy { it.id }.filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }

    /** The njump address of the note this one answers, built from a checked id (never from the text); null when it names nothing findable. */
    fun replyTargetUrl(event: NostrEvent): String? =
        event.repliedToId?.let { NostrKeys.noteId(it) }?.let { NJUMP_BASE + it }

    /** The last line of a reply: a break, the mark, and the address as an anchor. Empty for a note that answers nothing findable. */
    private fun replyLine(event: NostrEvent): String {
        val url = escapePlain(replyTargetUrl(event) ?: return "")
        return "<br>" + REPLY_MARK + " <a href=\"" + url + "\">" + url + "</a>"
    }

    private fun titleOf(html: String): String = RssDescriptionParser.titleFromBody(html)

    /** The note text as HTML: the plain parts escaped, each https link (see [linkSpans]) wrapped as an anchor whose address is escaped too. */
    private fun escapeToHtml(text: String): String = buildString {
        var cursor = 0
        for (span in linkSpans(text)) {
            append(escapePlain(text.substring(cursor, span.first)))
            val url = escapePlain(text.substring(span.first, span.last + 1))
            append("<a href=\"").append(url).append("\">").append(url).append("</a>")
            cursor = span.last + 1
        }
        append(escapePlain(text.substring(cursor)))
    }

    private fun escapePlain(text: String): String = buildString {
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
