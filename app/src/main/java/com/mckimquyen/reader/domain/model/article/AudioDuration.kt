package com.mckimquyen.reader.domain.model.article

object AudioDuration {
    private const val SECONDS_PER_MINUTE = 60
    private const val MAX_PARTS = 3

    /** Parses `SS`, `MM:SS` or `HH:MM:SS`; null for blank, negative or malformed input. */
    fun parse(text: String?): Int? {
        val parts = text?.trim()?.takeIf { it.isNotEmpty() }?.split(':') ?: return null
        if (parts.size > MAX_PARTS) return null
        val numbers = parts.map { it.trim().toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        return numbers.fold(0) { total, part -> total * SECONDS_PER_MINUTE + part }
    }
}
