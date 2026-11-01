package com.mckimquyen.reader.domain.steelman

/** Keeps a headline short enough to sit on the card; cuts at a word boundary when it can. */
object DualPerspectiveTruncator {
    const val MAX_HEADLINE_CHARS = 140

    /** About four lines at the card font size; the lower half has less room because it also holds source and cue. */
    const val MAX_LOWER_HEADLINE_CHARS = 90
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")

    data class Result(val text: String, val wasCut: Boolean)

    fun truncate(raw: String, maxChars: Int = MAX_HEADLINE_CHARS): Result {
        val clean = raw.trim().replace(WHITESPACE, " ")
        if (clean.length <= maxChars) return Result(clean, false)
        val head = clean.take(maxChars)
        val lastSpace = head.lastIndexOf(' ')
        val cut = if (lastSpace > 0) head.substring(0, lastSpace) else head
        return Result(cut.trimEnd() + ELLIPSIS, true)
    }
}
