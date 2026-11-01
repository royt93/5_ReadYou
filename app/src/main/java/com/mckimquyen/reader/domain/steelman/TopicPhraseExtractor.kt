package com.mckimquyen.reader.domain.steelman

/** Picks up to [MAX_PHRASES] proper-noun style phrases from a headline to use as the topic of an archive search. */
object TopicPhraseExtractor {
    const val MAX_PHRASES = 3
    const val MIN_PHRASE_LENGTH = 4
    private const val MAX_RUN_TOKENS = 3
    private const val MIN_ACRONYM_LETTERS = 2

    private val STOP_WORDS = setOf(
        "The", "A", "An", "In", "On", "At", "And", "Of", "For", "To", "With", "By", "From",
        "This", "That", "It", "He", "She", "They", "We", "What", "Why", "How", "When", "Who",
        "Is", "Are", "As", "But", "Or", "If", "After", "Before", "Over", "Under",
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "January", "February", "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December",
        "Thứ", "Tháng", "Một", "Những", "Các", "Trong", "Ngoài",
    )

    private val WORD_SPLIT = Regex("\\s+")
    private val POSSESSIVE_SUFFIXES = listOf("’s", "'s")

    fun extract(title: String, feedName: String): List<String> {
        if (title.isBlank()) return emptyList()
        val tokens = title.trim().split(WORD_SPLIT).map(::cleanToken)
        val candidates = linkedSetOf<String>()

        var index = 0
        while (index < tokens.size) {
            if (!isCapitalized(tokens[index])) {
                index++
                continue
            }
            var end = index
            while (end < tokens.size && isCapitalized(tokens[end])) end++
            collectWindows(tokens.subList(index, end), clauseStart = index == 0, out = candidates)
            index = end
        }

        return candidates
            .filter { phrase ->
                phrase.length >= MIN_PHRASE_LENGTH &&
                    !feedName.contains(phrase, ignoreCase = true) &&
                    !phrase.contains(feedName, ignoreCase = true)
            }
            .sortedWith(compareByDescending<String> { it.length }.thenBy { title.indexOf(it, ignoreCase = true) })
            .distinctBy { it.lowercase() }
            .take(MAX_PHRASES)
    }

    private fun collectWindows(run: List<String>, clauseStart: Boolean, out: MutableSet<String>) {
        for (from in run.indices) {
            for (to in from until minOf(run.size, from + MAX_RUN_TOKENS)) {
                val window = run.subList(from, to + 1)
                if (window.first() in STOP_WORDS || window.last() in STOP_WORDS) continue
                // A lone word at the start of the headline is capitalized by position, not because it is a name.
                if (window.size == 1 && clauseStart && from == 0 && !looksLikeAcronymOrName(window.first())) continue
                out += window.joinToString(" ")
            }
        }
    }

    private fun cleanToken(raw: String): String {
        var token = raw
        POSSESSIVE_SUFFIXES.forEach { token = token.removeSuffix(it) }
        return token.trim { !it.isLetterOrDigit() }
    }

    private fun isCapitalized(token: String): Boolean = token.isNotEmpty() && token.first().isUpperCase()

    private fun looksLikeAcronymOrName(token: String): Boolean {
        val letters = token.filter { it.isLetter() }
        val allCaps = letters.length >= MIN_ACRONYM_LETTERS && letters.all { it.isUpperCase() }
        return allCaps || token.drop(1).any { it.isUpperCase() }
    }
}
