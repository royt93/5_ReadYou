package com.mckimquyen.reader.domain.model.bounty

import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import java.time.Instant
import java.time.ZoneId

/**
 * Mechanical digest of the user's own archive (BOUNTY-02): verbatim quotes, per-day timeline, recurring
 * capitalized phrases and per-source headlines. Pure Kotlin; reads list metadata only (FIX-17).
 * It does not verify facts and "recurring phrases" is not named-entity recognition.
 */
object DossierBuilder {

    const val MIN_ARTICLES = 2
    const val MAX_SUMMARY_SENTENCES = 3
    const val MAX_SENTENCE_CHARS = 240
    const val MAX_PHRASES = 8
    const val MIN_ARTICLES_PER_PHRASE = 2
    const val MAX_HEADLINES_PER_SOURCE = 3
    const val SUMMARY_DEDUPE_JACCARD_THRESHOLD = 0.8

    private const val MAX_PHRASE_TOKENS = 4
    private const val MIN_PHRASE_TOKEN_LENGTH = 2
    private const val MIN_ACRONYM_LETTERS = 2

    private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+|[\\r\\n]+")
    private val CLAUSE_SPLIT = Regex("[,;:()\\[\\]\"“”—–]")
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")
    private val POSSESSIVE_SUFFIXES = listOf("’s", "'s")

    private val STOP_WORDS: Set<String> = setOf(
        "The", "A", "An", "In", "On", "At", "And", "Of", "For", "To", "With", "By", "From", "This", "That",
        "It", "He", "She", "They", "We", "What", "Why", "How", "When", "Who", "Is", "Are", "As", "But", "Or", "If",
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
        "November", "December",
        "Thứ", "Tháng",
    )

    fun build(
        result: BountyInvestigationResult,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Dossier? {
        if (result.articles.size < MIN_ARTICLES) return null
        val newestFirst = result.articles.sortedByDescending { it.article.date.time }
        val feedIds = newestFirst.map { it.article.feedId }.distinct()
        return Dossier(
            query = result.query,
            summary = buildSummary(result.query, newestFirst),
            timeline = buildTimeline(newestFirst, zone),
            phrases = buildPhrases(result.query, newestFirst),
            framings = if (feedIds.size >= MIN_SOURCES_FOR_FRAMINGS) buildFramings(newestFirst) else emptyList(),
            sourceCount = feedIds.size,
            generatedAtMillis = nowMillis,
        )
    }

    private const val MIN_SOURCES_FOR_FRAMINGS = 2

    private fun ArticleListItemWithFeed.ref() =
        DossierArticleRef(article.id, article.title, feed.name, article.date)

    // ---- Summary ----

    private fun buildSummary(query: String, newestFirst: List<ArticleListItemWithFeed>): List<QuotedSentence> {
        val kept = mutableListOf<QuotedSentence>()
        val keptWords = mutableListOf<Set<String>>()
        for (item in newestFirst) {
            for (text in listOf(item.article.title, item.article.shortDescription)) {
                for (raw in text.split(SENTENCE_SPLIT)) {
                    if (kept.size >= MAX_SUMMARY_SENTENCES) return kept
                    val sentence = raw.trim()
                    if (sentence.isEmpty() || !containsPhrase(sentence, query)) continue
                    val quote = safePrefix(sentence, MAX_SENTENCE_CHARS)
                    val words = wordSet(quote)
                    if (keptWords.any { jaccard(it, words) >= SUMMARY_DEDUPE_JACCARD_THRESHOLD }) continue
                    kept += QuotedSentence(quote, item.feed.name, item.article.date, item.article.id)
                    keptWords += words
                }
            }
        }
        return kept
    }

    /** Whole-word/phrase match: both neighbours must be non-letter/digit, so "disrupt" never hits "disruption". */
    private fun containsPhrase(text: String, phrase: String): Boolean {
        if (phrase.isEmpty()) return false
        var from = 0
        while (true) {
            val at = text.indexOf(phrase, from, ignoreCase = true)
            if (at < 0) return false
            val before = if (at == 0) ' ' else text[at - 1]
            val afterIndex = at + phrase.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = at + 1
        }
    }

    private fun wordSet(text: String): Set<String> =
        text.lowercase().split(WORD_SPLIT).filter { it.isNotEmpty() }.toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val union = a.union(b).size
        return a.intersect(b).size.toDouble() / union
    }

    private fun safePrefix(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end).trimEnd()
    }

    // ---- Timeline ----

    private fun buildTimeline(newestFirst: List<ArticleListItemWithFeed>, zone: ZoneId): List<TimelineEntry> =
        newestFirst
            .groupBy { Instant.ofEpochMilli(it.article.date.time).atZone(zone).toLocalDate() }
            .map { (day, items) -> TimelineEntry(day, items.map { it.ref() }) }
            .sortedByDescending { it.day }

    // ---- Recurring phrases ----

    private fun buildPhrases(query: String, newestFirst: List<ArticleListItemWithFeed>): List<RecurringPhrase> {
        val articlesPerPhrase = HashMap<String, MutableSet<String>>()
        // The source's own name ("BBC") recurs because of attribution, not because it is a subject of the story.
        val feedNames = newestFirst.map { it.feed.name }.distinct()
        for (item in newestFirst) {
            val found = HashSet<String>()
            for (text in listOf(item.article.title, item.article.shortDescription)) {
                for (sentence in text.split(SENTENCE_SPLIT)) {
                    for (clause in sentence.split(CLAUSE_SPLIT)) collectPhrases(clause, found)
                }
            }
            found.filterNot { phrase -> isQueryRelated(phrase, query) || feedNames.any { containsPhrase(it, phrase) } }
                .forEach { articlesPerPhrase.getOrPut(it) { mutableSetOf() }.add(item.article.id) }
        }
        val counts = articlesPerPhrase.mapValues { it.value.size }
            .filterValues { it >= MIN_ARTICLES_PER_PHRASE }
        return counts
            .filter { (phrase, count) -> counts.none { (other, otherCount) -> isSubsumed(phrase, count, other, otherCount) } }
            .map { RecurringPhrase(it.key, it.value) }
            .sortedWith(compareByDescending<RecurringPhrase> { it.articleCount }.thenBy { it.phrase })
            .take(MAX_PHRASES)
    }

    private fun isQueryRelated(phrase: String, query: String): Boolean =
        containsPhrase(phrase, query) || containsPhrase(query, phrase)

    /** A shorter phrase is redundant when a longer one containing it recurs at least as often. */
    private fun isSubsumed(phrase: String, count: Int, other: String, otherCount: Int): Boolean =
        other.length > phrase.length && otherCount >= count && containsPhrase(other, phrase)

    private fun collectPhrases(clause: String, out: MutableSet<String>) {
        val tokens = clause.trim().split(Regex("\\s+")).map { cleanToken(it) }
        var index = 0
        while (index < tokens.size) {
            if (!isCapitalized(tokens[index])) {
                index++
                continue
            }
            var end = index
            while (end < tokens.size && isCapitalized(tokens[end])) end++
            addWindows(tokens.subList(index, end), clauseStart = index == 0, out = out)
            index = end
        }
    }

    private fun addWindows(run: List<String>, clauseStart: Boolean, out: MutableSet<String>) {
        for (from in run.indices) {
            for (to in from until minOf(run.size, from + MAX_PHRASE_TOKENS)) {
                val window = run.subList(from, to + 1)
                if (window.first() in STOP_WORDS || window.last() in STOP_WORDS) continue
                // A lone word at clause start is capitalized by position, not by being a name: keep acronyms
                // and internal capitals (OpenAI), drop ordinary words ("Services").
                if (window.size == 1 && clauseStart && from == 0 && !looksLikeName(window.first())) continue
                out += window.joinToString(" ")
            }
        }
    }

    private fun cleanToken(raw: String): String {
        var token = raw
        POSSESSIVE_SUFFIXES.forEach { token = token.removeSuffix(it) }
        return token.trim { !it.isLetterOrDigit() }
    }

    private fun isCapitalized(token: String): Boolean =
        token.length >= MIN_PHRASE_TOKEN_LENGTH && token.first().isUpperCase()

    private fun looksLikeName(token: String): Boolean {
        val letters = token.filter { it.isLetter() }
        val allCaps = letters.length >= MIN_ACRONYM_LETTERS && letters.all { it.isUpperCase() }
        return allCaps || token.drop(1).any { it.isUpperCase() }
    }

    // ---- Framings ----

    private fun buildFramings(newestFirst: List<ArticleListItemWithFeed>): List<SourceFraming> =
        newestFirst.groupBy { it.article.feedId }.values
            .map { items ->
                SourceFraming(items.first().feed.name, items.take(MAX_HEADLINES_PER_SOURCE).map { it.ref() })
            }
            .sortedWith(compareByDescending<SourceFraming> { it.headlines.first().date.time }.thenBy { it.feedName })
}
