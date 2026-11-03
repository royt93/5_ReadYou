package com.mckimquyen.reader.domain.model.galaxy

import java.util.Locale

/** Meaningful words of a headline, used to relate two articles in the galaxy. Pure and deterministic. */
object TitleWords {
    const val MIN_WORD_CHARS = 4

    private val nonWord = Regex("[^\\p{L}\\p{Nd}]+")

    /** Mirrors the search engine's list plus common English function words that are 4+ letters long. */
    private val stopWords = setOf(
        "about", "after", "also", "been", "before", "from", "have", "into", "just", "like", "more", "most", "over",
        "says", "said", "than", "that", "their", "them", "then", "there", "these", "they", "this", "those", "through",
        "what", "when", "where", "which", "while", "will", "with", "would", "your",
        // Function words and generic headline verbs found joining unrelated stories in a real archive.
        "does", "want", "wants", "against", "inside", "third", "leave", "weekend", "could", "should", "being",
        "because", "between", "during", "before", "still", "first", "years", "year", "back",
        "watch", "video", "live", "news", "update", "updates",
        "như", "những", "được", "trong", "người", "cũng", "nhưng", "khi", "theo", "với", "cho", "này", "các",
    )

    fun extract(title: String): Set<String> =
        title.lowercase(Locale.ROOT)
            .split(nonWord)
            .filter { it.length >= MIN_WORD_CHARS && it !in stopWords && !it.all(Char::isDigit) }
            .toSet()
}
