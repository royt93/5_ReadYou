package com.mckimquyen.reader.domain.radar

import java.security.MessageDigest

/** Turns article HTML into the capped plain text stored in snapshots, and hashes it for change detection. */
object SnapshotNormalizer {
    /** Per-snapshot cap on stored text (chars). Keeps one row far below CursorWindow limits (FIX-17). */
    const val MAX_SNAPSHOT_CHARS = 40_000

    private const val HEX_RADIX = 16
    private const val HEX_BYTE_MASK = 0xff
    private const val HEX_BYTE_WIDTH = 2

    private val SCRIPT_OR_STYLE = Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun toPlainText(html: String): String {
        val noTags = html.replace(SCRIPT_OR_STYLE, " ").replace(TAG, " ")
        // &amp; last so "&amp;lt;" becomes "&lt;", not "<".
        val decoded = noTags
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
        val collapsed = decoded.replace(WHITESPACE, " ").trim()
        if (collapsed.length <= MAX_SNAPSHOT_CHARS) return collapsed
        val cut = collapsed.take(MAX_SNAPSHOT_CHARS)
        return if (Character.isHighSurrogate(cut.last())) cut.dropLast(1) else cut
    }

    /** SHA-256 hex of the lower-cased, whitespace-collapsed text, so cosmetic edits do not count as changes. */
    fun hash(text: String): String {
        val canonical = text.lowercase().replace(WHITESPACE, " ").trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and HEX_BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0') }
    }
}
