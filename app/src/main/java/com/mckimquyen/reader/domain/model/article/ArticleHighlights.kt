package com.mckimquyen.reader.domain.model.article

import androidx.annotation.Keep

/**
 * Domain model representing AI-extracted or heuristic article highlights and key takeaways.
 *
 * @param tldr A 1-2 sentence executive overview of the article.
 * @param keyTakeaways A list of 3-5 concise, high-value takeaway bullet points.
 * @param readingTimeSavedMin Estimated minutes saved by reading the summary instead of the full article.
 * @param tags Salient topic tags extracted from the article.
 * @param isOfflineFallback True if generated via smart offline heuristics rather than online Gemini API.
 */
@Keep
data class ArticleHighlights(
    val tldr: String = "",
    val keyTakeaways: List<String> = emptyList(),
    val readingTimeSavedMin: Int = 1,
    val tags: List<String> = emptyList(),
    val isOfflineFallback: Boolean = false,
) {
    /**
     * Formats the highlights into a clean, human-readable plain text string suitable for copying
     * to the clipboard or sharing across social/messaging apps.
     */
    fun formatAsPlainText(): String = buildString {
        if (tldr.isNotBlank()) {
            appendLine("📌 TL;DR:")
            appendLine(tldr)
            appendLine()
        }
        if (keyTakeaways.isNotEmpty()) {
            appendLine("💡 Key Takeaways:")
            keyTakeaways.forEachIndexed { index, takeaway ->
                appendLine("${index + 1}. $takeaway")
            }
            appendLine()
        }
        if (readingTimeSavedMin > 0) {
            appendLine("⏱ Estimated time saved: ~$readingTimeSavedMin min")
        }
        if (tags.isNotEmpty()) {
            appendLine()
            appendLine(tags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })
        }
    }.trim()
}
