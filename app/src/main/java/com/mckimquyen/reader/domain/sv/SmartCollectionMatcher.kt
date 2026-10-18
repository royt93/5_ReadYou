package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollectionArticle
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartCollectionMatcher @Inject constructor(
    private val smartCollectionDao: SmartCollectionDao,
    private val semanticSearchEngine: SemanticSearchEngine,
    private val articleDao: ArticleDao,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {

    companion object {
        const val REEVALUATION_PAGE_SIZE = 200
    }

    suspend fun matchNewArticles(accountId: Int, candidateArticles: List<ArticleWithFeed>): Int =
        withContext(dispatcher) {
            if (candidateArticles.isEmpty()) return@withContext 0
            val collections = smartCollectionDao.getCollectionsByAccount(accountId)
            if (collections.isEmpty()) return@withContext 0

            semanticSearchEngine.cacheEmbeddings(candidateArticles)
            val matchedAt = System.currentTimeMillis()
            val matches = collections.flatMap { collection ->
                semanticSearchEngine.rank(
                    query = collection.query,
                    articles = candidateArticles,
                    minScoreThreshold = collection.minScoreThreshold,
                ).map { result ->
                    SmartCollectionArticle(
                        collectionId = collection.id,
                        articleId = result.articleWithFeed.article.id,
                        score = result.score,
                        matchedAt = matchedAt,
                    )
                }
            }
            if (matches.isEmpty()) return@withContext 0
            smartCollectionDao.insertMatches(matches).count { it != -1L }
        }

    suspend fun reevaluateCollection(accountId: Int, collectionId: String): Int =
        withContext(dispatcher) {
            val collection = smartCollectionDao.getCollectionById(collectionId)
                ?.takeIf { it.accountId == accountId }
                ?: return@withContext 0
            val matches = mutableListOf<SmartCollectionArticle>()
            var offset = 0
            val matchedAt = System.currentTimeMillis()
            while (true) {
                val candidates = articleDao.queryArticlesWithFeedPage(
                    accountId = accountId,
                    limit = REEVALUATION_PAGE_SIZE,
                    offset = offset,
                )
                if (candidates.isEmpty()) break
                semanticSearchEngine.cacheEmbeddings(candidates)
                matches += semanticSearchEngine.rank(
                    query = collection.query,
                    articles = candidates,
                    minScoreThreshold = collection.minScoreThreshold,
                ).map { result ->
                    SmartCollectionArticle(
                        collectionId = collectionId,
                        articleId = result.articleWithFeed.article.id,
                        score = result.score,
                        matchedAt = matchedAt,
                    )
                }
                offset += candidates.size
            }
            smartCollectionDao.replaceMatches(collectionId, matches)
            matches.size
        }
}
