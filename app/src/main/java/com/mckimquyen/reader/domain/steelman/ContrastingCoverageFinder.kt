package com.mckimquyen.reader.domain.steelman

import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed

object ContrastingCoverageFinder {
    const val MAX_RESULTS = 5
    const val CANDIDATE_LIMIT = 40

    fun find(
        currentArticleId: String,
        currentFeedId: String,
        phrase: String,
        candidates: List<ArticleListItemWithFeed>,
    ): List<ContrastingArticle> {
        if (phrase.isBlank()) return emptyList()

        return candidates
            .filter { item ->
                val article = item.article
                article.id != currentArticleId &&
                    article.feedId != currentFeedId &&
                    (WholePhraseMatcher.containsPhrase(article.title, phrase) ||
                        WholePhraseMatcher.containsPhrase(article.shortDescription, phrase))
            }
            .mapNotNull { item ->
                val cue = ContrarianCueLexicon.find(item.article.title) ?: return@mapNotNull null
                ContrastingArticle(
                    articleId = item.article.id,
                    title = item.article.title,
                    feedName = item.feed.name,
                    date = item.article.date,
                    cue = cue,
                )
            }
            .sortedByDescending { it.date.time }
            .take(MAX_RESULTS)
    }
}
