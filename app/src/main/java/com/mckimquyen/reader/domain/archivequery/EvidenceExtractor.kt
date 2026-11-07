package com.mckimquyen.reader.domain.archivequery

import com.mckimquyen.reader.domain.steelman.WholePhraseMatcher
import org.jsoup.Jsoup
import java.text.BreakIterator
import java.time.Instant
import java.util.Locale

/** One verbatim sentence taken from an article body, with the article it came from. */
data class ArchiveEvidence(
    val articleId: String,
    val title: String,
    val feedName: String,
    val publishedAt: Instant,
    val quote: String,
    val quoteTruncated: Boolean,
    val topicMatched: Boolean,
)

object EvidenceExtractor {

    const val MAX_TEXT_CHARS = 40_000
    const val MAX_QUOTE_CHARS = 300
    private const val MIN_FALLBACK_SENTENCE_CHARS = 15
    private val NON_VISIBLE_TAGS = "script, style, noscript, template, head"
    private val WHITESPACE = Regex("\\s+")

    /** HTML to visible plain text; never fetches resources. Capped without splitting a surrogate pair. */
    fun toPlainText(html: String, maxChars: Int = MAX_TEXT_CHARS): String {
        if (html.isBlank()) return ""
        val body = Jsoup.parseBodyFragment(html).body()
        body.select(NON_VISIBLE_TAGS).remove()
        val text = body.text().replace(WHITESPACE, " ").trim()
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        return if (cut.isNotEmpty() && Character.isHighSurrogate(cut.last())) cut.dropLast(1) else cut
    }

    /**
     * Picks at most one sentence: the first containing a whole-word topic token, otherwise the first sentence of
     * at least [MIN_FALLBACK_SENTENCE_CHARS] chars. Returns null when the body has no usable text.
     */
    fun extract(article: RankedArchiveArticle, bodyHtml: String, topicTokens: Set<String>): ArchiveEvidence? {
        val text = toPlainText(bodyHtml)
        if (text.isEmpty()) return null
        val sentences = splitSentences(text)
        val tokens = topicTokens.filter { it.isNotBlank() }
        val matched = sentences.firstOrNull { s -> tokens.any { WholePhraseMatcher.containsPhrase(s, it) } }
        val chosen = matched ?: sentences.firstOrNull { it.length >= MIN_FALLBACK_SENTENCE_CHARS } ?: return null
        val (quote, truncated) = capQuote(chosen)
        return ArchiveEvidence(
            articleId = article.articleId,
            title = article.title,
            feedName = article.feedName,
            publishedAt = article.date,
            quote = quote,
            quoteTruncated = truncated,
            topicMatched = matched != null,
        )
    }

    private fun splitSentences(text: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT)
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) result.add(sentence)
            start = end
            end = iterator.next()
        }
        return result
    }

    /** Cuts at a word boundary when possible and never inside a surrogate pair; the result stays a substring. */
    private fun capQuote(sentence: String): Pair<String, Boolean> {
        if (sentence.length <= MAX_QUOTE_CHARS) return sentence to false
        var cut = sentence.take(MAX_QUOTE_CHARS)
        if (Character.isHighSurrogate(cut.last())) cut = cut.dropLast(1)
        val lastSpace = cut.lastIndexOf(' ')
        if (lastSpace > 0) cut = cut.substring(0, lastSpace)
        return cut.trimEnd() to true
    }
}
