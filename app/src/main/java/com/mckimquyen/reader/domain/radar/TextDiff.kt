package com.mckimquyen.reader.domain.radar

enum class DiffType { SAME, REMOVED, ADDED }

data class DiffPart(val type: DiffType, val text: String)

/** Word-level LCS diff. Pure Kotlin; no dependency. */
object TextDiff {
    /** Above this many DP cells the middle section is reported as one removal + one addition instead of an LCS. */
    private const val MAX_DP_CELLS = 4_000_000L
    private val WHITESPACE = Regex("\\s+")

    fun wordDiff(old: String, new: String): List<DiffPart> {
        val a = tokens(old)
        val b = tokens(new)
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++

        val midA = a.subList(prefix, a.size - suffix)
        val midB = b.subList(prefix, b.size - suffix)
        val raw = ArrayList<DiffPart>()
        a.subList(0, prefix).forEach { raw += DiffPart(DiffType.SAME, it) }
        raw += middle(midA, midB)
        a.subList(a.size - suffix, a.size).forEach { raw += DiffPart(DiffType.SAME, it) }
        return merge(raw)
    }

    private fun middle(a: List<String>, b: List<String>): List<DiffPart> {
        if (a.isEmpty() || b.isEmpty() || (a.size + 1L) * (b.size + 1L) > MAX_DP_CELLS) {
            return a.map { DiffPart(DiffType.REMOVED, it) } + b.map { DiffPart(DiffType.ADDED, it) }
        }
        val width = b.size + 1
        // lcs[i * width + j] = LCS length of a[i..] and b[j..].
        val lcs = IntArray((a.size + 1) * width)
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                lcs[i * width + j] = if (a[i] == b[j]) {
                    lcs[(i + 1) * width + j + 1] + 1
                } else {
                    maxOf(lcs[(i + 1) * width + j], lcs[i * width + j + 1])
                }
            }
        }
        val out = ArrayList<DiffPart>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out += DiffPart(DiffType.SAME, a[i]); i++; j++ }
                lcs[(i + 1) * width + j] >= lcs[i * width + j + 1] -> out += DiffPart(DiffType.REMOVED, a[i++])
                else -> out += DiffPart(DiffType.ADDED, b[j++])
            }
        }
        while (i < a.size) out += DiffPart(DiffType.REMOVED, a[i++])
        while (j < b.size) out += DiffPart(DiffType.ADDED, b[j++])
        return out
    }

    private fun merge(parts: List<DiffPart>): List<DiffPart> {
        val out = ArrayList<DiffPart>()
        val buf = StringBuilder()
        var type: DiffType? = null
        for (p in parts) {
            if (p.type != type) {
                if (type != null) out += DiffPart(type, buf.toString())
                buf.setLength(0)
                type = p.type
            } else {
                buf.append(' ')
            }
            buf.append(p.text)
        }
        if (type != null) out += DiffPart(type, buf.toString())
        return out
    }

    private fun tokens(text: String): List<String> = text.trim().split(WHITESPACE).filter(String::isNotEmpty)
}
