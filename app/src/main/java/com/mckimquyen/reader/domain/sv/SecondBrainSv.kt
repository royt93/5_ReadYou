package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.secondbrain.BrainArticle
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraphBuilder
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the Second Brain graph from data already on the device: concepts detected by
 * [SemanticSearchEngine] plus tags the user already generated in saved AI summaries. No AI call is made.
 */
@Singleton
class SecondBrainSv @Inject constructor(
    private val articleDao: ArticleDao,
    private val semanticSearchEngine: SemanticSearchEngine,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend fun load(
        accountId: Int,
        fromTime: Long = Long.MIN_VALUE,
        toTime: Long = Long.MAX_VALUE,
    ): BrainGraph = withContext(defaultDispatcher) {
        val articles = articleDao.queryRecentArticlesWithFeed(accountId, MAX_ARTICLES).map(::toBrainArticle)
        BrainGraphBuilder.build(articles, fromTime, toTime)
    }

    internal fun toBrainArticle(item: ArticleWithFeed): BrainArticle {
        val article = item.article
        val text = "${article.title} ${article.shortDescription}"
        val detected = semanticSearchEngine.detectConceptsStrict(text).map { it.lowercase() }
        val tags = ArticleHighlightsExtractor.deserialize(article.aiSummary)?.tags.orEmpty()
        return BrainArticle(
            id = article.id,
            title = article.title,
            date = article.date.time,
            concepts = (detected + tags).toSet(),
        )
    }

    private companion object {
        const val MAX_ARTICLES = 500
    }
}
