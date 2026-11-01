package com.mckimquyen.reader.domain.steelman

object WholePhraseMatcher {

    /** Case-insensitive match that needs a non-letter/digit (or the text edge) on both sides of the phrase. */
    fun containsPhrase(text: String, phrase: String): Boolean {
        val needle = phrase.trim()
        if (needle.isEmpty()) return false
        var from = 0
        while (true) {
            val at = text.indexOf(needle, from, ignoreCase = true)
            if (at < 0) return false
            val before = if (at == 0) ' ' else text[at - 1]
            val afterIndex = at + needle.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = at + 1
        }
    }
}
