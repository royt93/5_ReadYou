package com.mckimquyen.reader.domain.model.quote

data class Truncated(val text: String, val wasCut: Boolean)

object QuoteTextTruncator {
    const val QUOTE_MAX_CHARS = 280
    const val ELLIPSIS = "…"

    /** Cắt theo ranh giới từ, không tách cặp surrogate, thêm "…" khi có cắt. */
    fun truncate(text: String, max: Int = QUOTE_MAX_CHARS): Truncated {
        if (text.length <= max) return Truncated(text, false)
        var end = max
        if (Character.isHighSurrogate(text[end - 1])) end--
        val lastSpace = text.lastIndexOf(' ', end - 1)
        val cut = if (lastSpace > 0) lastSpace else end
        return Truncated(text.substring(0, cut).trimEnd() + ELLIPSIS, true)
    }
}
