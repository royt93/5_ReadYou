package com.mckimquyen.reader.ui.component.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Returns [this] unchanged (same instance) when [enabled] is false. Otherwise returns a copy with a bold
 * style layered over the leading letters of each word. The text, existing styles and string annotations
 * (links) are carried over verbatim, so selection, copy and screen readers still see the original text.
 */
fun AnnotatedString.withBionic(enabled: Boolean): AnnotatedString {
    if (!enabled || text.isEmpty()) return this
    val ranges = BionicTextFormatter.boldRanges(text)
    if (ranges.isEmpty()) return this
    val source = this
    return buildAnnotatedString {
        append(source)
        for (range in ranges) addStyle(BionicTextFormatter.BOLD, range.first, range.last + 1)
    }
}

/**
 * Bionic Reading: bold the first ~40% of every word so the eye can skim.
 *
 * Pure and allocation-light so it can run on every paragraph while the reader is composed.
 * It only decides WHICH UTF-16 index ranges to bold; the caller applies them as styles on top of the
 * existing text, so links, annotations and the underlying string (copy / accessibility) are untouched.
 *
 * Rules:
 *  - A word is a run of letters/digits; an apostrophe or hyphen BETWEEN two word characters keeps it one
 *    word ("don't", "well-known").
 *  - Length is counted in user-visible characters: a base letter plus its combining marks counts once, a
 *    surrogate pair counts once. A bold span never ends between a base and its marks or inside a pair.
 *  - Scripts written without spaces (CJK) form one long word and bold their leading characters.
 *  - URLs and e-mail-like tokens are skipped entirely.
 */
object BionicTextFormatter {
    const val BOLD_FRACTION = 0.4

    /** Layered on top of whatever style a word already has (italic, link colour, ...). */
    val BOLD = SpanStyle(fontWeight = FontWeight.Bold)

    fun boldRanges(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        val result = ArrayList<IntRange>()
        var i = 0
        val n = text.length
        while (i < n) {
            val cp = text.codePointAt(i)
            if (!isWordChar(cp)) {
                i += Character.charCount(cp)
                continue
            }
            val wordStart = i
            var wordEnd = i
            while (i < n) {
                val c = text.codePointAt(i)
                val size = Character.charCount(c)
                if (isWordChar(c) || isMark(c)) {
                    i += size
                    wordEnd = i
                } else if (isJoiner(c) && i + size < n && isWordChar(text.codePointAt(i + size))) {
                    i += size
                } else {
                    break
                }
            }
            if (!looksLikeUrlOrAddress(text, wordStart, wordEnd)) {
                boldPrefix(text, wordStart, wordEnd)?.let(result::add)
            }
        }
        return result
    }

    /** Bold range for [start, end) or null; counts user-visible characters, never splitting a cluster. */
    private fun boldPrefix(text: String, start: Int, end: Int): IntRange? {
        val total = countClusters(text, start, end)
        if (total == 0) return null
        val wanted = kotlin.math.ceil(total * BOLD_FRACTION).toInt().coerceIn(1, total)
        var i = start
        var seen = 0
        while (i < end) {
            i = nextClusterEnd(text, i, end)
            seen++
            if (seen == wanted) return start until i
        }
        return start until end
    }

    private fun countClusters(text: String, start: Int, end: Int): Int {
        var i = start
        var count = 0
        while (i < end) {
            i = nextClusterEnd(text, i, end)
            count++
        }
        return count
    }

    /** End (exclusive) of the user-visible character starting at [from]: base code point plus trailing marks. */
    private fun nextClusterEnd(text: String, from: Int, end: Int): Int {
        var next = from + Character.charCount(text.codePointAt(from))
        while (next < end && isMark(text.codePointAt(next))) next += Character.charCount(text.codePointAt(next))
        return next
    }

    private fun isWordChar(cp: Int) = Character.isLetterOrDigit(cp)

    private fun isMark(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun isJoiner(cp: Int) = cp == '\''.code || cp == '’'.code || cp == '-'.code

    /**
     * True when the word belongs to a URL / e-mail / path rather than prose. The expensive token scan only runs
     * when a cheap neighbour check shows one of the markers ('.', '/', ':', '@') right next to the word, so
     * ordinary prose words cost a couple of character comparisons and no allocation.
     */
    private fun looksLikeUrlOrAddress(text: String, start: Int, end: Int): Boolean {
        val before = if (start > 0) text[start - 1] else ' '
        val after = if (end < text.length) text[end] else ' '
        if (!isUrlMarker(before) && !isUrlMarker(after)) return false

        var tokenStart = start
        while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) tokenStart--
        var tokenEnd = end
        while (tokenEnd < text.length && !text[tokenEnd].isWhitespace()) tokenEnd++

        var hasDot = false
        var hasSlash = false
        var hasAt = false
        for (i in tokenStart until tokenEnd) {
            when (text[i]) {
                '.' -> hasDot = true
                '/' -> hasSlash = true
                '@' -> hasAt = true
            }
        }
        val hasScheme = text.indexOf("://", tokenStart).let { it in 0 until tokenEnd }
        return hasScheme ||
            text.regionMatches(tokenStart, "www.", 0, 4, ignoreCase = true) ||
            (hasAt && hasDot) ||
            (hasSlash && hasDot)
    }

    private fun isUrlMarker(c: Char) =
        c == '.' || c == '/' || c == ':' || c == '@' || c == '?' || c == '=' || c == '&' || c == '#' || c == '%'
}
