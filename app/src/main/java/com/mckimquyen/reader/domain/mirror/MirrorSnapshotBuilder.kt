package com.mckimquyen.reader.domain.mirror

/**
 * Turns article plain text into a bounded, control-character-free [MirrorSnapshot]. The HTML to text step happens
 * before this (the reader already does it with `HtmlCompat`), which keeps this object testable on a plain JVM.
 */
object MirrorSnapshotBuilder {
    const val MAX_TITLE_CHARS = 300
    const val MAX_SOURCE_CHARS = 120

    private val paragraphBreak = Regex("\n\\s*\n+")
    private val lineFeed = '\n'

    fun fromPlainText(
        title: String,
        source: String,
        dateMillis: Long,
        plainText: String,
        version: Long,
    ): MirrorSnapshot {
        var truncated = false

        val paragraphs = clean(plainText)
            .split(paragraphBreak)
            .map { it.replace(lineFeed, ' ').trim() }
            .filter { it.isNotEmpty() }
            .map { paragraph ->
                if (paragraph.length <= MirrorLimits.MAX_PARAGRAPH_CHARS) paragraph
                else { truncated = true; cut(paragraph, MirrorLimits.MAX_PARAGRAPH_CHARS) }
            }
            .let { all ->
                if (all.size <= MirrorLimits.MAX_PARAGRAPHS) all
                else { truncated = true; all.take(MirrorLimits.MAX_PARAGRAPHS) }
            }
            .toMutableList()

        var snapshot = build(title, source, dateMillis, paragraphs, truncated, version)
        // Multi-byte text can make the JSON larger than the character caps suggest, so enforce the real byte cap too.
        while (paragraphs.isNotEmpty() && snapshot.toJson().toByteArray(Charsets.UTF_8).size > MirrorLimits.MAX_SNAPSHOT_BYTES) {
            paragraphs.removeAt(paragraphs.lastIndex)
            truncated = true
            snapshot = build(title, source, dateMillis, paragraphs, truncated, version)
        }
        return snapshot
    }

    private fun build(
        title: String, source: String, dateMillis: Long, paragraphs: List<String>, truncated: Boolean, version: Long,
    ) = MirrorSnapshot(
        title = cut(clean(title).replace(lineFeed, ' ').trim(), MAX_TITLE_CHARS),
        source = cut(clean(source).replace(lineFeed, ' ').trim(), MAX_SOURCE_CHARS),
        dateMillis = dateMillis,
        paragraphs = paragraphs.toList(),
        truncated = truncated,
        version = version,
    )

    /** Drops control characters (keeping tab and line breaks), line separators and unpaired surrogates. */
    private fun clean(text: String): String {
        val unified = text.replace("\r\n", "\n").replace('\r', '\n')
        return buildString(unified.length) {
            var i = 0
            while (i < unified.length) {
                val ch = unified[i]
                when {
                    Character.isHighSurrogate(ch) ->
                        if (i + 1 < unified.length && Character.isLowSurrogate(unified[i + 1])) {
                            append(ch).append(unified[i + 1]); i++
                        }
                    Character.isLowSurrogate(ch) -> Unit
                    ch == ' ' || ch == ' ' || ch == '\u0085' -> Unit
                    ch.isISOControl() && ch != '\t' && ch != '\n' -> Unit
                    else -> append(ch)
                }
                i++
            }
        }
    }

    /** Cuts to at most [limit] chars without leaving half of a surrogate pair at the end. */
    private fun cut(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val end = if (Character.isHighSurrogate(text[limit - 1])) limit - 1 else limit
        return text.substring(0, end)
    }
}
