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

    /** Where to scroll for a body fraction: a list item plus how far into it (0..1) to go. */
    data class BodyScrollTarget(val listIndex: Int, val withinItem: Float)

    /**
     * Fraction of the body above the first visible item's top edge. Consecutive paragraphs share one list item, so a
     * text-only article has a single body item and the scroll offset inside that item is what tells 5% from 90%.
     * Before the body is 0, after it is 1; an unmeasured item (size 0) counts as not scrolled.
     */
    fun bodyFractionFromList(
        firstVisibleIndex: Int,
        firstVisibleOffsetPx: Int,
        firstVisibleSizePx: Int,
        bodyStart: Int,
        bodyCount: Int,
    ): Float {
        if (bodyCount <= 0 || firstVisibleIndex < bodyStart) return 0f
        if (firstVisibleIndex >= bodyStart + bodyCount) return 1f
        val inside = if (firstVisibleSizePx > 0) {
            (firstVisibleOffsetPx.toFloat() / firstVisibleSizePx).coerceIn(0f, 1f)
        } else {
            0f
        }
        return (((firstVisibleIndex - bodyStart) + inside) / bodyCount).coerceIn(0f, 1f)
    }

    /** Inverse of [bodyFractionFromList]: every body item counts the same share of the body. */
    fun scrollTargetFromBodyFraction(fraction: Float, bodyStart: Int, bodyCount: Int): BodyScrollTarget {
        val start = bodyStart.coerceAtLeast(0)
        if (bodyCount <= 0) return BodyScrollTarget(start, 0f)
        val position = fraction.coerceIn(0f, 1f) * bodyCount
        val item = position.toInt().coerceAtMost(bodyCount - 1)
        return BodyScrollTarget(start + item, (position - item).coerceIn(0f, 1f))
    }

    /** Whole-list fraction (the format persisted in `ArticleReadPosition.progress`) for a body fraction. */
    fun listFractionFromBodyFraction(fraction: Float, bodyStart: Int, bodyCount: Int, totalItems: Int): Float {
        if (totalItems <= SINGLE_ITEM) return 0f
        val index = scrollTargetFromBodyFraction(fraction, bodyStart, bodyCount).listIndex
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
