package com.mckimquyen.reader.ui.page.triage

import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed

data class TriageUiState(
    val isLoading: Boolean = true,
    val articles: List<ArticleListItemWithFeed> = emptyList(),
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val savedCount: Int = 0,
    val skippedCount: Int = 0,
    val totalXpEarned: Long = 0L,
    val isInboxZero: Boolean = false,
) {
    val currentArticle: ArticleListItemWithFeed?
        get() = articles.firstOrNull()

    val nextArticle: ArticleListItemWithFeed?
        get() = if (articles.size > 1) articles[1] else null
}
