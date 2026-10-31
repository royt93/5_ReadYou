package com.mckimquyen.reader.domain.model.bounty

import com.mckimquyen.reader.domain.model.quote.QuoteSelection

/**
 * Chuẩn hoá cụm từ được người dùng bôi đen trước khi gửi cho Agent điều tra (BOUNTY-01).
 * Cắt lấy câu đầu tiên, tối đa 100 ký tự, loại bỏ dấu câu biên, và escape an toàn cho SQL LIKE.
 */
object BountyQuerySanitizer {

    const val MAX_QUERY_LENGTH: Int = 100
    private const val MIN_QUERY_LENGTH: Int = 2

    private val SENTENCE_SPLIT_REGEX = Regex("[.;\\n\\r]+")
    private val TRIM_CHARS = charArrayOf(' ', '\t', '\n', '\r', '"', '\'', '`', '“', '”', '‘', '’', '(', ')', '[', ']', '{', '}', '.', ',', '!', '?', ':', ';', '-')

    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        // Tách TRƯỚC khi gộp khoảng trắng: QuoteSelection.sanitize biến xuống dòng thành dấu cách,
        // làm mất ranh giới câu cần dùng ở đây.
        val firstSentence = raw.split(SENTENCE_SPLIT_REGEX).firstOrNull { it.isNotBlank() } ?: return null
        val normalized = QuoteSelection.sanitize(firstSentence) ?: return null

        // Tỉa dấu câu biên
        val trimmed = normalized.trim { it in TRIM_CHARS }

        if (trimmed.length < MIN_QUERY_LENGTH) return null

        // Giới hạn độ dài an toàn 100 ký tự
        if (trimmed.length <= MAX_QUERY_LENGTH) return trimmed

        // Không cắt giữa cặp surrogate (emoji, chữ ngoài BMP): lùi một đơn vị nếu ký tự cuối là high surrogate.
        var end = MAX_QUERY_LENGTH
        if (Character.isHighSurrogate(trimmed[end - 1])) end--
        return trimmed.substring(0, end).trimEnd { it in TRIM_CHARS }.takeIf { it.length >= MIN_QUERY_LENGTH }
    }

    /**
     * Escape các ký tự đặc biệt của SQL LIKE (`\`, `%`, `_`) với ký tự escape mặc định là `\`.
     * Sử dụng kèm clause `ESCAPE '\'` trong câu truy vấn Room.
     */
    fun escapeLikeLiteral(query: String): String {
        return query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
