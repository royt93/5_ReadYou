package com.mckimquyen.reader.domain.model.article

data class ArticleBody(val articleId: String, val rawDescription: String, val fullContent: String?)

/** Kích thước byte giúp bảo toàn UTF-8, kể cả ký tự nằm giữa hai đoạn. */
data class ArticleBodyLengths(val rawBytes: Int, val fullBytes: Int?)

data class ArticleBodyChunk(val raw: ByteArray, val full: ByteArray?)
