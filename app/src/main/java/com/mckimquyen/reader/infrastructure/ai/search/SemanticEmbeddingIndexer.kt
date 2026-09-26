package com.mckimquyen.reader.infrastructure.ai.search

import com.mckimquyen.reader.domain.repository.ArticleDao
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the persistent index immediately after sync, before the next search keystroke. */
@Singleton
class SemanticEmbeddingIndexer @Inject constructor(
    private val articleDao: ArticleDao,
    private val semanticSearchEngine: SemanticSearchEngine,
) {
    suspend fun indexRecent(
        accountId: Int,
        limit: Int = DEFAULT_INDEX_LIMIT,
    ): CacheUpdateStats {
        if (limit <= 0) return CacheUpdateStats.EMPTY
        val articles = articleDao.queryRecentArticlesWithFeed(accountId, limit)
        return semanticSearchEngine.cacheEmbeddings(articles)
    }

    companion object {
        const val DEFAULT_INDEX_LIMIT = 200
    }
}
