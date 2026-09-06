package com.mckimquyen.reader.infrastructure.ai.search

import androidx.annotation.Keep
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed

/**
 * Kết quả tìm kiếm ngữ nghĩa đại diện cho bài viết và mức độ liên quan về mặt khái niệm.
 */
@Keep
data class SemanticSearchResult(
    val articleWithFeed: ArticleWithFeed,
    val score: Float,
    val matchedConcepts: List<String> = emptyList(),
) {
    val percentage: Int get() = (score * 100).toInt().coerceIn(0, 100)
}
