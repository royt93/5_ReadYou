package com.mckimquyen.reader.domain.readposition

import kotlin.math.roundToInt

/**
 * Converts one approximate reading position between normal reading (LazyColumn body items), RSVP (token index) and
 * TTS (paragraph offset in plain text). Positions are fractions of the article body in 0..1; they are close, not exact,
 * because list items (images, tables, code) are not the same length as the text they stand for.
 */
object ReadingPositionMapper {
    /** Below this the reader is treated as not started, so RSVP/TTS begin at the top. */
    const val RESTART_BELOW_FRACTION = 0.02f

    /** From this on the article is treated as finished, so RSVP/TTS begin at the top. */
    const val TREAT_AS_FINISHED_FRACTION = 0.98f

    /** RSVP must advance at least this many words before closing it moves the reader. */
    const val MIN_RSVP_WORDS_TO_SCROLL = 20

    private const val PARAGRAPH_BREAK = "\n\n"
    private const val SINGLE_ITEM = 1

    /** Fraction of the body that sits above [firstVisibleIndex]; before the body is 0, after it is 1. */
    fun bodyFractionFromList(firstVisibleIndex: Int, bodyStart: Int, bodyCount: Int): Float {
        if (bodyCount <= SINGLE_ITEM) return 0f
        return ((firstVisibleIndex - bodyStart).toFloat() / (bodyCount - 1)).coerceIn(0f, 1f)
    }

    /** List index of the body item at [fraction]; truncates like the existing scroll restore. */
    fun listIndexFromBodyFraction(fraction: Float, bodyStart: Int, bodyCount: Int): Int {
        val start = bodyStart.coerceAtLeast(0)
        if (bodyCount <= SINGLE_ITEM) return start
        return start + (fraction.coerceIn(0f, 1f) * (bodyCount - 1)).toInt()
    }

    /** Whole-list fraction (the format persisted in `ArticleReadPosition.progress`) for a body fraction. */
    fun listFractionFromBodyFraction(fraction: Float, bodyStart: Int, bodyCount: Int, totalItems: Int): Float {
        if (totalItems <= SINGLE_ITEM) return 0f
        val index = listIndexFromBodyFraction(fraction, bodyStart, bodyCount)
        return (index.toFloat() / (totalItems - 1)).coerceIn(0f, 1f)
    }

    fun tokenIndexFromFraction(fraction: Float, tokenCount: Int): Int {
        if (tokenCount <= 0) return 0
        return (fraction.coerceIn(0f, 1f) * (tokenCount - 1)).roundToInt()
    }

    fun fractionFromTokenIndex(index: Int, tokenCount: Int): Float {
        if (tokenCount <= SINGLE_ITEM) return 0f
        return (index.toFloat() / (tokenCount - 1)).coerceIn(0f, 1f)
    }

    /** Start of the paragraph at or before [fraction] of the text; paragraphs are separated by a blank line. */
    fun paragraphStartOffset(plainText: String, fraction: Float): Int {
        if (plainText.isEmpty()) return 0
        val limit = (plainText.length * fraction.coerceIn(0f, 1f)).toInt()
        var best = 0
        var from = 0
        while (true) {
            val at = plainText.indexOf(PARAGRAPH_BREAK, from)
            if (at < 0) break
            val start = at + PARAGRAPH_BREAK.length
            if (start > limit) break
            best = start
            from = start
        }
        return best
    }

    fun shouldHandoffFromRsvp(startIndex: Int, currentIndex: Int): Boolean =
        currentIndex - startIndex >= MIN_RSVP_WORDS_TO_SCROLL
}
