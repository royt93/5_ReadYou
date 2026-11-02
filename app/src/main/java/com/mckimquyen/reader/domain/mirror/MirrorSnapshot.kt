package com.mckimquyen.reader.domain.mirror

/** The only thing the mirror ever sends: one article reduced to plain text. */
data class MirrorSnapshot(
    val title: String,
    val source: String,
    val dateMillis: Long,
    val paragraphs: List<String>,
    val truncated: Boolean,
    val version: Long,
) {
    /**
     * Hand-written JSON (no library on the JVM-only path). Besides the mandatory escapes it also escapes `<`, `>`, `&`
     * and the two Unicode line separators, so the text can never close a script element or break a JS string even if
     * somebody later inlines this JSON into a page.
     */
    fun toJson(): String = buildString {
        append("{\"title\":"); appendJsonString(title)
        append(",\"source\":"); appendJsonString(source)
        append(",\"date\":").append(dateMillis)
        append(",\"paragraphs\":[")
        paragraphs.forEachIndexed { i, p -> if (i > 0) append(','); appendJsonString(p) }
        append("],\"truncated\":").append(truncated)
        append(",\"version\":").append(version)
        append('}')
    }

    companion object {
        const val EMPTY_JSON = """{"title":"","source":"","date":0,"paragraphs":[],"truncated":false,"version":0}"""

        private const val FIRST_PRINTABLE = 0x20

        private fun StringBuilder.appendJsonString(value: String) {
            append('"')
            value.forEach { ch ->
                when {
                    ch == '"' -> append("\\\"")
                    ch == '\\' -> append("\\\\")
                    ch == '\n' -> append("\\n")
                    ch == '\r' -> append("\\r")
                    ch == '\t' -> append("\\t")
                    ch.code < FIRST_PRINTABLE || ch == '<' || ch == '>' || ch == '&' ||
                        ch == ' ' || ch == ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}
