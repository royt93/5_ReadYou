package com.mckimquyen.reader.domain.model.digest

import com.mckimquyen.reader.domain.model.article.Article

const val DAILY_DIGEST_MAX_ARTICLES = 20

data class DigestArticleSource(
    val id: String,
    val title: String,
    val content: String,
    val link: String,
    val publishedAt: Long,
)

object DigestArticleSelector {
    fun select(articles: List<Article>): List<DigestArticleSource> = articles
        .asSequence()
        .filter { it.isUnread }
        .mapNotNull { article ->
            val content = sequenceOf(article.aiSummary, article.fullContent, article.rawDescription, article.shortDescription)
                .filterNotNull()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?: return@mapNotNull null
            if (article.title.isBlank() || article.link.isBlank()) return@mapNotNull null
            DigestArticleSource(
                id = article.id,
                title = article.title.trim(),
                content = content,
                link = article.link,
                publishedAt = article.date.time,
            )
        }
        .distinctBy { it.id }
        .sortedByDescending { it.publishedAt }
        .take(DAILY_DIGEST_MAX_ARTICLES)
        .toList()
}
