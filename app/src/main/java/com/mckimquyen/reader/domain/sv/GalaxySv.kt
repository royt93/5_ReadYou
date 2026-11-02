package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraph
import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraphBuilder
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the knowledge galaxy from articles the user already read: one star per article, concepts from
 * [SemanticSearchEngine] plus tags in saved AI summaries. Offline; no AI call and no article body is read.
 */
@Singleton
class GalaxySv @Inject constructor(
    private val articleDao: ArticleDao,
    private val semanticSearchEngine: SemanticSearchEngine,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend fun load(
        accountId: Int,
        from: Long = Long.MIN_VALUE,
        to: Long = Long.MAX_VALUE,
    ): GalaxyGraph {
        val rows = articleDao.queryReadArticleListWithFeed(accountId, MAX_READ_ARTICLES)
        return withContext(defaultDispatcher) { GalaxyGraphBuilder.build(rows.map(::toStar), from, to) }
    }

    private fun toStar(item: ArticleListItemWithFeed): GalaxyStar {
        val article = item.article
        val detected = semanticSearchEngine.detectConceptsStrict("${article.title} ${article.shortDescription}")
        val tags = ArticleHighlightsExtractor.deserialize(article.aiSummary)?.tags.orEmpty()
        return GalaxyStar(
            id = article.id,
            title = article.title,
            feedName = item.feed.name,
            date = article.date.time,
            concepts = (detected + tags).mapTo(linkedSetOf()) { it.lowercase() },
        )
    }

    companion object {
        /** Same as [GalaxyGraphBuilder.MAX_STARS]: reading more rows than can be drawn only costs time. */
        const val MAX_READ_ARTICLES = GalaxyGraphBuilder.MAX_STARS
    }
}
