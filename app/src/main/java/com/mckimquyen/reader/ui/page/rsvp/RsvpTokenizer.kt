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

    // HTML tags that terminate a block/paragraph: closing them marks a paragraph boundary.
    private val blockCloseTagRegex =
        Regex("</(?:p|div|h[1-6]|li|blockquote|section|article|tr|td|th)\\s*>", RegexOption.IGNORE_CASE)
    private val brTagRegex = Regex("<br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val paragraphSeparatorRegex = Regex("\\n\\s*\\n")

    private const val PARAGRAPH_SEPARATOR = "\n\n"

    private const val PARAGRAPH_END_DELAY_MS = 250L
    private const val SENTENCE_END_DELAY_MS = 200L
    private const val CLAUSE_END_DELAY_MS = 80L
    private const val LONG_WORD_DELAY_MS = 40L
    private const val LONG_WORD_THRESHOLD = 10

    private val sentenceEndChars = charArrayOf('.', '!', '?', ':')
    private val clauseEndChars = charArrayOf(',', ';', '-', '—')

    /**
     * Bóc tag HTML + giải mã entity, gộp mọi khoảng trắng (kể cả ngắt đoạn) thành 1 space.
     * Lưu ý: hàm này **không** giữ ranh giới đoạn văn — dùng [splitIntoParagraphs] khi cần
     * biết đoạn văn nào với đoạn văn nào (ví dụ để tính [RsvpToken.isParagraphBreak]).
     */
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

    /**
     * Tách nội dung thành các đoạn văn đã làm sạch, **giữ ranh giới đoạn trước khi collapse whitespace**.
     * Ranh giới được nhận diện từ: thẻ đóng block (`</p>`, `</div>`, `</h1..h6>`, `</li>`...),
     * `<br>`/`<br/>` (kể cả cặp `<br><br>`), và chuỗi `\n\n` (hoặc `\r\n\r\n`) trong text gốc.
     */
    fun splitIntoParagraphs(rawText: String): List<String> {
        val normalized = rawText
            .replace(brTagRegex, PARAGRAPH_SEPARATOR)
            .replace(blockCloseTagRegex, PARAGRAPH_SEPARATOR)
        return normalized
            .split(paragraphSeparatorRegex)
            .map { cleanHtml(it) }
            .filter { it.isNotBlank() }
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
            delay += PARAGRAPH_END_DELAY_MS
        }
        if (trimmed.endsWithAny(sentenceEndChars)) {
            delay += SENTENCE_END_DELAY_MS
        } else if (trimmed.endsWithAny(clauseEndChars)) {
            delay += CLAUSE_END_DELAY_MS
        }
        if (trimmed.length > LONG_WORD_THRESHOLD) {
            delay += LONG_WORD_DELAY_MS
        }
        return delay
    }

    private fun String.endsWithAny(chars: CharArray): Boolean =
        isNotEmpty() && chars.any { this[length - 1] == it }

    fun tokenize(content: String): List<RsvpToken> {
        // Paragraph boundaries must be resolved on the raw text, before whitespace is collapsed —
        // otherwise every paragraph separator is flattened to a single space and no token can ever
        // be marked as a paragraph break.
        val paragraphs = splitIntoParagraphs(content)
        if (paragraphs.isEmpty()) return emptyList()

        val tokens = mutableListOf<RsvpToken>()

        for (pIndex in paragraphs.indices) {
            val rawWords = paragraphs[pIndex].split(whitespaceRegex).filter { it.isNotBlank() }

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
