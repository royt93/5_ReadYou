package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionArticle
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.ArticleEmbeddingDao
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class SmartCollectionReviewRegressionTest {
    private val dao = mockk<SmartCollectionDao>(relaxed = true)
    private val articles = mockk<ArticleDao>()
    private val embeddings = mockk<ArticleEmbeddingDao>(relaxed = true).also {
        coEvery { it.getByArticleIds(any()) } returns emptyList()
    }
    private val matcher = SmartCollectionMatcher(
        dao, SemanticSearchEngine(embeddings), articles, Dispatchers.Unconfined,
    )
    private val collection = SmartCollection(
        id = COLLECTION_ID,
        accountId = ACCOUNT_ID,
        name = "AI",
        query = QUERY,
        minScoreThreshold = 0f,
    )

    @Test
    fun incrementalMatchingPersistsEveryMatchBeyondSearchResultLimit() = runBlocking {
        val candidates = candidates(INCREMENTAL_BATCH_SIZE)
        coEvery { dao.getCollectionsByAccount(ACCOUNT_ID) } returns listOf(collection)
        val captured = slot<List<SmartCollectionArticle>>()
        coEvery { dao.insertMatches(capture(captured)) } answers {
            List(captured.captured.size) { 1L }
        }

        val count = matcher.matchNewArticles(ACCOUNT_ID, candidates)

        assertEquals(candidates.size, count)
        assertEquals(candidates.map { it.article.id }.toSet(), captured.captured.map { it.articleId }.toSet())
    }

    @Test
    fun reevaluationPersistsAllMatchesAcrossFullAndPartialPages() = runBlocking {
        val candidates = candidates(SmartCollectionMatcher.REEVALUATION_PAGE_SIZE + INCREMENTAL_BATCH_SIZE)
        coEvery { dao.getCollectionById(COLLECTION_ID) } returns collection
        coEvery { articles.queryArticlesWithFeedPage(ACCOUNT_ID, SmartCollectionMatcher.REEVALUATION_PAGE_SIZE, any()) } answers {
            candidates.drop(thirdArg<Int>()).take(SmartCollectionMatcher.REEVALUATION_PAGE_SIZE)
        }
        val captured = slot<List<SmartCollectionArticle>>()
        coEvery { dao.replaceMatches(COLLECTION_ID, capture(captured)) } returns Unit

        val count = matcher.reevaluateCollection(ACCOUNT_ID, COLLECTION_ID)

        assertEquals(candidates.size, count)
        assertEquals(candidates.map { it.article.id }.toSet(), captured.captured.map { it.articleId }.toSet())
    }

    private fun candidates(count: Int): List<ArticleWithFeed> {
        val feed = Feed(
            id = "feed", name = "Feed", url = "https://example.com/rss",
            groupId = "group", accountId = ACCOUNT_ID,
        )
        return List(count) { index ->
            ArticleWithFeed(
                Article(
                    id = "article-$index", date = Date(0), title = QUERY,
                    rawDescription = "", shortDescription = QUERY,
                    link = "https://example.com/$index", feedId = feed.id, accountId = ACCOUNT_ID,
                ),
                feed,
            )
        }
    }

    private companion object {
        const val ACCOUNT_ID = 1
        const val COLLECTION_ID = "review"
        const val QUERY = "artificial intelligence"
        const val INCREMENTAL_BATCH_SIZE = 60
    }
}
