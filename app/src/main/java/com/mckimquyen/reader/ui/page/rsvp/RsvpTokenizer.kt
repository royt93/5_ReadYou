package com.mckimquyen.reader.ui.page.rsvp

data class RsvpToken(
    val fullWord: String,
    val prefix: String,
    val orpChar: Char,
    val suffix: String,
    val orpIndex: Int,
    val extraDelayMs: Long = 0L,
    val isParagraphBreak: Boolean = false
)

object RsvpTokenizer {

    private val htmlTagRegex = Regex("<[^>]*>")
    private val whitespaceRegex = Regex("\\s+")

    fun cleanHtml(rawText: String): String {
        return rawText
            .replace(htmlTagRegex, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun calculateOrpIndex(word: String): Int {
        val len = word.length
        return when {
            len <= 1 -> 0
            len in 2..5 -> 1
            len in 6..9 -> 2
            len in 10..13 -> 3
            else -> 4
        }.coerceIn(0, (len - 1).coerceAtLeast(0))
    }

    fun calculateExtraDelayMs(word: String, isParagraphEnd: Boolean): Long {
        var delay = 0L
        val trimmed = word.trim()
        if (isParagraphEnd) {
            delay += 250L
        }
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?") || trimmed.endsWith(":")) {
            delay += 200L
        } else if (trimmed.endsWith(",") || trimmed.endsWith(";") || trimmed.endsWith("-") || trimmed.endsWith("—")) {
            delay += 80L
        }
        if (trimmed.length > 10) {
            delay += 40L
        }
        return delay
    }

    fun tokenize(content: String): List<RsvpToken> {
        val cleaned = cleanHtml(content)
        if (cleaned.isBlank()) return emptyList()

        val paragraphs = cleaned.split("\n\n", "\r\n\r\n")
        val tokens = mutableListOf<RsvpToken>()

        for (pIndex in paragraphs.indices) {
            val p = paragraphs[pIndex].trim()
            if (p.isBlank()) continue
            val rawWords = p.split(whitespaceRegex).filter { it.isNotBlank() }

            for (wIndex in rawWords.indices) {
                val rawWord = rawWords[wIndex]
                val isParagraphEnd = (wIndex == rawWords.lastIndex) && (pIndex < paragraphs.lastIndex)
                val orp = calculateOrpIndex(rawWord)
                val prefix = if (orp > 0) rawWord.substring(0, orp) else ""
                val orpChar = if (rawWord.isNotEmpty()) rawWord[orp] else ' '
                val suffix = if (orp + 1 < rawWord.length) rawWord.substring(orp + 1) else ""
                val delay = calculateExtraDelayMs(rawWord, isParagraphEnd)

                tokens.add(
                    RsvpToken(
                        fullWord = rawWord,
                        prefix = prefix,
                        orpChar = orpChar,
                        suffix = suffix,
                        orpIndex = orp,
                        extraDelayMs = delay,
                        isParagraphBreak = isParagraphEnd
                    )
                )
            }
        }
        return tokens
    }
}
