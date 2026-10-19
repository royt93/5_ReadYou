package com.mckimquyen.reader.domain.model.quote

object QuoteSelection {
    private val WHITESPACE_RUN = Regex("[\\s\\u00A0]+")

    /** Chuẩn hoá đoạn bôi đen: gộp khoảng trắng/xuống dòng thành một dấu cách, trả null nếu rỗng. */
    fun sanitize(raw: String?): String? =
        raw?.replace(WHITESPACE_RUN, " ")?.trim()?.takeIf { it.isNotEmpty() }
}
