package com.mckimquyen.reader.domain.headline

import java.util.Locale

/** Languages for which the local headline lexicon has coverage. ES/FR/DE lexicons are experimental. */
enum class HeadlineLanguage { ENGLISH, VIETNAMESE, SPANISH, FRENCH, GERMAN, UNSUPPORTED }

enum class HeadlineTone { POSITIVE, UNCLEAR, NEGATIVE }

data class HeadlineSignal(
    val language: HeadlineLanguage,
    val clickbaitScore: Int,
    val tone: HeadlineTone,
)

/** Deterministic local lexical signals. These are not fact-checks, claims of political bias, or author intent. */
object HeadlineAnalyzer {
    const val MAX_TITLE_CHARS = 240
    const val CLICKBAIT_THRESHOLD = 70
    private const val BAIT_PHRASE_WEIGHT = 36
    private const val CURIOSITY_WEIGHT = 17
    private const val QUESTION_WEIGHT = 9
    private const val REPEATED_PUNCTUATION_WEIGHT = 14
    private const val UPPERCASE_WEIGHT = 14
    private const val NUMBERED_LIST_WEIGHT = 14
    private const val INTENSIFIER_WEIGHT = 7
    private const val MIN_TONE_EVIDENCE = 2
    private const val TONE_MARGIN = 2

    private val whitespace = Regex("\\s+")
    private val letters = Regex("[^\\p{L}\\p{M}\\p{Nd}]+")
    private val repeatedPunctuation = Regex("[!?]{2,}")
    private val leadingListNumber = Regex("^\\s*\\d{1,2}\\s*[.):\\-]")

    fun analyze(title: String): HeadlineSignal? {
        val bounded = title.take(MAX_TITLE_CHARS).trim()
        if (bounded.isBlank()) return null
        val language = detectLanguage(bounded) ?: return null
        val normalized = normalize(bounded)
        val tokens = tokens(normalized)
        val lexicon = language.lexicon()
        val clickbait = clickbaitScore(bounded, normalized, tokens, lexicon)
        val (positive, negative) = toneEvidence(normalized, tokens, lexicon)
        val tone = when {
            positive >= MIN_TONE_EVIDENCE && positive - negative >= TONE_MARGIN -> HeadlineTone.POSITIVE
            negative >= MIN_TONE_EVIDENCE && negative - positive >= TONE_MARGIN -> HeadlineTone.NEGATIVE
            else -> HeadlineTone.UNCLEAR
        }
        return HeadlineSignal(language, clickbait, tone)
    }

    fun shouldShowClickbait(score: Int): Boolean = score > CLICKBAIT_THRESHOLD

    private fun detectLanguage(text: String): HeadlineLanguage? {
        val normalized = normalize(text)
        val words = tokens(normalized)
        if (words.isEmpty()) return null

        // Vietnamese diacritics provide a strong positive clue; English words also match in Vietnamese headlines,
        // so test Vietnamese before English function-word frequency.
        if (text.any { it in VIETNAMESE_STRONG_DIACRITICS }) return HeadlineLanguage.VIETNAMESE

        val counts = listOf(
            HeadlineLanguage.VIETNAMESE to overlap(words, HeadlineLexicon.VI_MARKERS),
            HeadlineLanguage.ENGLISH to overlap(words, HeadlineLexicon.EN_MARKERS),
            HeadlineLanguage.SPANISH to overlap(words, HeadlineLexicon.ES_MARKERS),
            HeadlineLanguage.FRENCH to overlap(words, HeadlineLexicon.FR_MARKERS),
            HeadlineLanguage.GERMAN to overlap(words, HeadlineLexicon.DE_MARKERS),
        )
        val winner = counts.maxByOrNull { it.second } ?: return null
        val second = counts.asSequence().filter { it.first != winner.first }.maxOfOrNull { it.second } ?: 0
        if (winner.second < MIN_LANGUAGE_MARKERS || winner.second - second < LANGUAGE_MARGIN) return null
        return winner.first
    }

    private fun clickbaitScore(
        original: String,
        normalized: String,
        tokens: List<String>,
        lexicon: LanguageLexicon,
    ): Int {
        var score = 0
        val baitPhraseHits = lexicon.baitPhrases.count(normalized::contains)
        score += (baitPhraseHits * BAIT_PHRASE_WEIGHT).coerceAtMost(MAX_SCORE)
        if (lexicon.curiosityPatterns.any(normalized::contains)) score += CURIOSITY_WEIGHT
        if (original.count { it == '?' } >= 1) score += QUESTION_WEIGHT
        if (repeatedPunctuation.containsMatchIn(original)) score += REPEATED_PUNCTUATION_WEIGHT
        val letterCount = original.count(Char::isLetter)
        val uppercaseCount = original.count { it.isLetter() && it.isUpperCase() }
        if (letterCount >= MIN_UPPERCASE_LETTERS && uppercaseCount * 100 / letterCount >= UPPERCASE_PERCENT) {
            score += UPPERCASE_WEIGHT
        }
        if (leadingListNumber.containsMatchIn(original)) score += NUMBERED_LIST_WEIGHT
        if (tokens.any(lexicon.intensifiers::contains)) score += INTENSIFIER_WEIGHT
        return score.coerceIn(0, MAX_SCORE)
    }

    private fun toneEvidence(normalized: String, tokens: List<String>, lexicon: LanguageLexicon): Pair<Int, Int> {
        var positive = tokens.count(lexicon.positive::contains)
        var negative = tokens.count(lexicon.negative::contains)
        // Multi-word lexicon phrases count once; they are stored separately from token entries.
        positive += lexicon.positivePhrases.count(normalized::contains) * PHRASE_TONE_WEIGHT
        negative += lexicon.negativePhrases.count(normalized::contains) * PHRASE_TONE_WEIGHT
        return positive to negative
    }

    private fun overlap(words: List<String>, lexicon: Set<String>): Int = words.count(lexicon::contains)

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace(whitespace, " ")
        .trim()

    private fun tokens(text: String): List<String> = text.split(letters).filter(String::isNotBlank)

    private fun HeadlineLanguage.lexicon(): LanguageLexicon = when (this) {
        HeadlineLanguage.ENGLISH -> LanguageLexicon(HeadlineLexicon.EN_BAIT_PHRASES, HeadlineLexicon.EN_CURIOSITY_PATTERNS, HeadlineLexicon.EN_INTENSIFIERS, HeadlineLexicon.EN_POSITIVE, HeadlineLexicon.EN_NEGATIVE)
        HeadlineLanguage.VIETNAMESE -> LanguageLexicon(HeadlineLexicon.VI_BAIT_PHRASES, HeadlineLexicon.VI_CURIOSITY_PATTERNS, HeadlineLexicon.VI_INTENSIFIERS, HeadlineLexicon.VI_POSITIVE, HeadlineLexicon.VI_NEGATIVE)
        HeadlineLanguage.SPANISH -> LanguageLexicon(HeadlineLexicon.ES_BAIT_PHRASES, HeadlineLexicon.ES_CURIOSITY_PATTERNS, HeadlineLexicon.ES_INTENSIFIERS, HeadlineLexicon.ES_POSITIVE, HeadlineLexicon.ES_NEGATIVE)
        HeadlineLanguage.FRENCH -> LanguageLexicon(HeadlineLexicon.FR_BAIT_PHRASES, HeadlineLexicon.FR_CURIOSITY_PATTERNS, HeadlineLexicon.FR_INTENSIFIERS, HeadlineLexicon.FR_POSITIVE, HeadlineLexicon.FR_NEGATIVE)
        HeadlineLanguage.GERMAN -> LanguageLexicon(HeadlineLexicon.DE_BAIT_PHRASES, HeadlineLexicon.DE_CURIOSITY_PATTERNS, HeadlineLexicon.DE_INTENSIFIERS, HeadlineLexicon.DE_POSITIVE, HeadlineLexicon.DE_NEGATIVE)
        HeadlineLanguage.UNSUPPORTED -> error("Unsupported language has no lexicon")
    }

    private data class LanguageLexicon(
        val baitPhrases: List<String>,
        val curiosityPatterns: List<String>,
        val intensifiers: Set<String>,
        val positive: Set<String>,
        val negative: Set<String>,
        val positivePhrases: List<String> = emptyList(),
        val negativePhrases: List<String> = emptyList(),
    )

    private const val MIN_LANGUAGE_MARKERS = 1
    private const val LANGUAGE_MARGIN = 1
    private const val MIN_UPPERCASE_LETTERS = 5
    private const val UPPERCASE_PERCENT = 75
    private const val MAX_SCORE = 100
    private const val PHRASE_TONE_WEIGHT = 2
    // Unique Vietnamese letters/diacritics; common acute accents are insufficient to distinguish French/Spanish.
    private val VIETNAMESE_STRONG_DIACRITICS = setOf('ă','â','đ','ê','ô','ơ','ư','ả','ã','ạ','ấ','ầ','ẩ','ẫ','ậ','ắ','ằ','ẳ','ẵ','ặ','ẻ','ẽ','ẹ','ế','ề','ể','ễ','ệ','ỉ','ĩ','ị','ỏ','õ','ọ','ố','ồ','ổ','ỗ','ộ','ớ','ờ','ở','ỡ','ợ','ủ','ũ','ụ','ứ','ừ','ử','ữ','ự','ỷ','ỹ','ỵ')
}
