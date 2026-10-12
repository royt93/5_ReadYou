package com.mckimquyen.reader.domain.model.bilingual

sealed interface TranslationResult {
    data class Translated(val text: String) : TranslationResult

    /** Nothing to show: the paragraph is already in the target language, or its language is unknown. */
    data object SameLanguage : TranslationResult

    /** Model unavailable, offline during the first download, or the engine errored. Never cached. */
    data object Failed : TranslationResult
}

fun interface ParagraphTranslator {
    suspend fun translate(text: String, target: String): TranslationResult
}

/** Decides which paragraphs are worth sending to the translator. */
object ParagraphFilter {
    private const val MIN_LETTERS = 3

    fun shouldTranslate(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.count(Char::isLetter) < MIN_LETTERS) return false
        return !looksLikeUrlOrAddress(trimmed)
    }

    private fun looksLikeUrlOrAddress(token: String): Boolean {
        if (token.any(Char::isWhitespace)) return false
        return "://" in token ||
            token.startsWith("www.", ignoreCase = true) ||
            ('@' in token && '.' in token)
    }
}
