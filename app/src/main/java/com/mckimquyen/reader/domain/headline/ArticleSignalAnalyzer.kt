package com.mckimquyen.reader.domain.headline

import com.mckimquyen.reader.domain.radar.SnapshotNormalizer
import java.util.Locale

/** Local, deterministic body wording analysis for ECHO-01. This is not a bias or truth classifier. */
object ArticleSignalAnalyzer {
    const val MAX_ANALYZED_BODY_CHARS = 12_000
    const val MAX_FLAGGED_PASSAGES = 8
    const val MAX_PASSAGE_CHARS = 240
    const val MIN_BODY_CHARS = 30
    const val HYPE_NOTEWORTHY_THRESHOLD = 40
    const val HYPE_WARNING_THRESHOLD = 70

    // Density is measured per at-least-this-many words, so a single cue in a one-line item can never read as 100%.
    private const val MIN_WORDS_FOR_DENSITY = 40
    // A single promotional cue is weak evidence: it can be a proper noun ("Islamic Revolutionary Guard") or a quoted
    // word. Paragraphs/articles need independent corroboration before they can reach the noteworthy threshold.
    private const val MIN_DISTINCT_CUE_KINDS = 2
    private const val MAX_STACKED_HITS_PER_CATEGORY = 4
    private const val MIN_STACKED_HITS_FOR_CORROBORATION = 2
    private const val WORDS_PER_SCORE_WINDOW = 100
    private const val PROMOTIONAL_WEIGHT = 25
    private const val ABSOLUTE_CLAIM_WEIGHT = 15
    private const val INTENSIFIER_WEIGHT = 8
    private const val EXCLAMATION_WEIGHT = 10
    private const val PARAGRAPH_FLAG_THRESHOLD = 35
    // Overall hype leans on the word-weighted mean so one marketing sentence in a long factual article cannot
    // trigger a warning; the top paragraph only nudges it.
    private const val WEIGHTED_MEAN_SHARE_PERCENT = 80
    private const val TOP_PARAGRAPH_SHARE_PERCENT = 20
    // SnapshotNormalizer.toPlainText collapses every whitespace run (so paragraph breaks vanish) and is also used for
    // radar hashing, so it must not change. Mark paragraph boundaries before normalizing instead. U+0001 is not
    // matched by \s or by the tag regex, so it survives toPlainText untouched.
    private const val PARAGRAPH_MARK = '\u0001'
    private const val MIN_UPPERCASE_LETTERS = 8
    private const val UPPERCASE_PERCENT_THRESHOLD = 75

    private val blockTag = Regex("</?(?:p|div|br|li|ul|ol|h[1-6]|blockquote|section|article|tr|table|pre)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("<[a-zA-Z/][^>]*>")
    private val lineBreaks = Regex("[\\r\\n]+")
    private val wordTokens = Regex("[\\p{L}\\p{M}\\p{Nd}]+")
    private val repeatedExclamation = Regex("!{2,}")

    /**
     * Analyze Reader's current body HTML/plain text. Unsupported languages and empty/short bodies return null.
     * Only the first 12,000 plain-text UTF-16 characters are analyzed; a trailing high surrogate is excluded.
     */
    fun analyze(htmlOrPlainText: String, languageOverride: HeadlineLanguage? = null): ArticleSignalResult? {
        val plain = SnapshotNormalizer.toPlainText(markParagraphs(htmlOrPlainText))
        val bounded = safePrefix(plain, MAX_ANALYZED_BODY_CHARS)
        if (bounded.replace(PARAGRAPH_MARK, ' ').trim().length < MIN_BODY_CHARS) return null

        val paragraphs = bounded.split(PARAGRAPH_MARK).map(String::trim).filter(String::isNotBlank)
        if (paragraphs.isEmpty()) return null
        val body = paragraphs.joinToString(" ")

        val language = languageOverride ?: HeadlineAnalyzer.detectBodyLanguage(body) ?: return null
        if (language == HeadlineLanguage.UNSUPPORTED) return null

        val scored = paragraphs.mapIndexed { index, paragraph ->
            val (cues, score) = scoreParagraph(paragraph, language)
            ScoredPassage(index, paragraph, cues, score, wordCount(paragraph))
        }
        val totalWords = scored.sumOf(ScoredPassage::words).coerceAtLeast(1)
        val weightedMean = scored.sumOf { it.score * it.words } / totalWords
        val topParagraphScore = scored.maxOf(ScoredPassage::score)
        val overallHype = (
            weightedMean * WEIGHTED_MEAN_SHARE_PERCENT + topParagraphScore * TOP_PARAGRAPH_SHARE_PERCENT
            ) / 100

        val passages = scored.asSequence()
            .filter { it.score >= PARAGRAPH_FLAG_THRESHOLD && it.cues.isNotEmpty() }
            .sortedWith(compareByDescending<ScoredPassage> { it.score }.thenBy { it.index })
            .take(MAX_FLAGGED_PASSAGES)
            .map { it.toFlaggedPassage() }
            .toList()

        return ArticleSignalResult(
            language = language,
            hypeScore = overallHype.coerceIn(0, 100),
            tone = HeadlineAnalyzer.toneOf(body, language),
            flaggedPassages = passages,
        )
    }

    /** HTML: block-level tags end a paragraph and newlines are plain whitespace. Plain text: each line is one. */
    private fun markParagraphs(input: String): String =
        if (anyTag.containsMatchIn(input)) input.replace(blockTag, PARAGRAPH_MARK.toString())
        else input.replace(lineBreaks, PARAGRAPH_MARK.toString())

    private fun wordCount(text: String): Int = wordTokens.findAll(text).count().coerceAtLeast(1)

    /** Cues carry weights; multiple occurrences of promotional phrases or claims in a paragraph stack up to a cap. */
    private fun scoreParagraph(text: String, language: HeadlineLanguage): Pair<List<HypeCueCategory>, Int> {
        val normalized = HeadlineAnalyzer.normalizeText(text)
        val tokens = HeadlineAnalyzer.tokenize(normalized)
        val promoHits = countPhraseHits(normalized, phrases(language)).coerceAtMost(MAX_STACKED_HITS_PER_CATEGORY)
        val claimHits = countPhraseHits(normalized, absoluteClaims(language)).coerceAtMost(MAX_STACKED_HITS_PER_CATEGORY)
        val intensifierHits = tokens.count(HeadlineAnalyzer.bodyIntensifiers(language)::contains).coerceAtMost(MAX_STACKED_HITS_PER_CATEGORY)
        val exclamation = repeatedExclamation.containsMatchIn(text) || hasExcessiveUppercase(text)

        val raw = promoHits * PROMOTIONAL_WEIGHT +
            claimHits * ABSOLUTE_CLAIM_WEIGHT +
            intensifierHits * INTENSIFIER_WEIGHT +
            (if (exclamation) EXCLAMATION_WEIGHT else 0)

        val words = wordCount(text)
        val categories = mutableListOf<HypeCueCategory>()
        if (promoHits > 0) categories += HypeCueCategory.PROMOTIONAL_PHRASE
        if (claimHits > 0) categories += HypeCueCategory.ABSOLUTE_CLAIM
        if (intensifierHits > 0) categories += HypeCueCategory.INTENSIFIER
        if (exclamation) categories += HypeCueCategory.EXCLAMATION

        val density = (raw * WORDS_PER_SCORE_WINDOW / words.coerceAtLeast(MIN_WORDS_FOR_DENSITY)).coerceIn(0, 100)
        // One promotional phrase alone is capped below the noteworthy threshold; stacked hits of the same kind count
        // as corroboration (three promotional phrases in one paragraph is not a proper noun).
        val corroborated = categories.size >= MIN_DISTINCT_CUE_KINDS ||
            promoHits >= MIN_STACKED_HITS_FOR_CORROBORATION || claimHits >= MIN_STACKED_HITS_FOR_CORROBORATION
        val score = if (corroborated) density else density.coerceAtMost(HYPE_NOTEWORTHY_THRESHOLD - 1)
        return categories to score
    }

    private fun countPhraseHits(normalized: String, phrases: List<String>): Int =
        phrases.count { containsPhrase(normalized, it) }

    /**
     * Whole-word/phrase match: "disrupt" must not fire inside "disruption", nor "number one" inside prose about
     * sport. A phrase matches only when both ends fall on a non-letter/digit boundary.
     */
    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        var from = 0
        while (true) {
            val at = normalized.indexOf(phrase, from)
            if (at < 0) return false
            val before = if (at == 0) ' ' else normalized[at - 1]
            val afterIndex = at + phrase.length
            val after = if (afterIndex >= normalized.length) ' ' else normalized[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = at + 1
        }
    }

    private fun hasExcessiveUppercase(text: String): Boolean {
        val letters = text.filter(Char::isLetter)
        if (letters.length < MIN_UPPERCASE_LETTERS) return false
        val uppercase = letters.count(Char::isUpperCase)
        return uppercase * 100 / letters.length >= UPPERCASE_PERCENT_THRESHOLD
    }

    private fun phrases(language: HeadlineLanguage): List<String> = when (language) {
        HeadlineLanguage.ENGLISH -> HeadlineLexicon.EN_HYPE_PHRASES
        HeadlineLanguage.VIETNAMESE -> HeadlineLexicon.VI_HYPE_PHRASES
        HeadlineLanguage.SPANISH -> HeadlineLexicon.ES_HYPE_PHRASES
        HeadlineLanguage.FRENCH -> HeadlineLexicon.FR_HYPE_PHRASES
        HeadlineLanguage.GERMAN -> HeadlineLexicon.DE_HYPE_PHRASES
        HeadlineLanguage.UNSUPPORTED -> emptyList()
    }

    private fun absoluteClaims(language: HeadlineLanguage): List<String> = when (language) {
        HeadlineLanguage.ENGLISH -> HeadlineLexicon.EN_ABSOLUTE_CLAIMS
        HeadlineLanguage.VIETNAMESE -> HeadlineLexicon.VI_ABSOLUTE_CLAIMS
        HeadlineLanguage.SPANISH -> HeadlineLexicon.ES_ABSOLUTE_CLAIMS
        HeadlineLanguage.FRENCH -> HeadlineLexicon.FR_ABSOLUTE_CLAIMS
        HeadlineLanguage.GERMAN -> HeadlineLexicon.DE_ABSOLUTE_CLAIMS
        HeadlineLanguage.UNSUPPORTED -> emptyList()
    }

    private fun safePrefix(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val cut = text.substring(0, maxChars)
        return if (cut.isNotEmpty() && Character.isHighSurrogate(cut.last())) cut.dropLast(1) else cut
    }

    private fun ScoredPassage.toFlaggedPassage(): FlaggedPassage = FlaggedPassage(
        text = safePrefix(text, MAX_PASSAGE_CHARS).trim(),
        score = score,
        cues = cues,
    )

    private data class ScoredPassage(
        val index: Int,
        val text: String,
        val cues: List<HypeCueCategory>,
        val score: Int,
        val words: Int,
    )
}
