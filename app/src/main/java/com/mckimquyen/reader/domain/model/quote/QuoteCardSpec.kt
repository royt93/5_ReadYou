package com.mckimquyen.reader.domain.model.quote

/** Dữ liệu để vẽ một thẻ trích dẫn. `quote` đã được [QuoteSelection.sanitize]; renderer tự cắt theo [QuoteTextTruncator]. */
data class QuoteCardSpec(
    val quote: String,
    val feedName: String,
    val author: String?,
    val articleTitle: String,
)
