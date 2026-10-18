package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionArticle
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class SmartCollectionReevaluationTest {

    private val collectionDao = mockk<SmartCollectionDao>(relaxed = true)
    private val articleDao = mockk<ArticleDao>()
    private val searchEngine = mockk<SemanticSearchEngine>()
    private val matcher = SmartCollectionMatcher(
        smartCollectionDao = collectionDao,
        semanticSearchEngine = searchEngine,
        articleDao = articleDao,
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun reevaluateCollection_replacesOldMembershipWithNewQueryMatches() = runBlocking {
        val collection = collection(query = "renewable energy")
        val candidate = article("article-new", "Solar power investment")
        coEvery { collectionDao.getCollectionById(COLLECTION_ID) } returns collection
        coEvery { articleDao.queryArticlesWithFeedPage(ACCOUNT_ID, SmartCollectionMatcher.REEVALUATION_PAGE_SIZE, 0) } returns listOf(candidate)
        coEvery { articleDao.queryArticlesWithFeedPage(ACCOUNT_ID, SmartCollectionMatcher.REEVALUATION_PAGE_SIZE, 1) } returns emptyList()
        coEvery { searchEngine.cacheEmbeddings(listOf(candidate)) } returns mockk(relaxed = true)
        coEvery {
            searchEngine.rank(collection.query, listOf(candidate), collection.minScoreThreshold, any())
        } returns listOf(
            com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchResult(
                articleWithFeed = candidate,
                score = MATCH_SCORE,
                matchedConcepts = emptyList(),
            )
        )
        val captured = slot<List<SmartCollectionArticle>>()
        coEvery { collectionDao.replaceMatches(COLLECTION_ID, capture(captured)) } returns Unit

        val matchCount = matcher.reevaluateCollection(ACCOUNT_ID, COLLECTION_ID)

        assertEquals(1, matchCount)
        assertEquals(listOf("article-new"), captured.captured.map { it.articleId })
        assertEquals(MATCH_SCORE, captured.captured.single().score)
        coVerify(exactly = 2) { articleDao.queryArticlesWithFeedPage(eq(ACCOUNT_ID), eq(SmartCollectionMatcher.REEVALUATION_PAGE_SIZE), any()) }
        coVerify(exactly = 1) { collectionDao.replaceMatches(COLLECTION_ID, any()) }
    }

    @Test
    fun reevaluateCollection_noArticles_clearsPreviousMembership() = runBlocking {
        coEvery { collectionDao.getCollectionById(COLLECTION_ID) } returns collection()
        coEvery { articleDao.queryArticlesWithFeedPage(ACCOUNT_ID, SmartCollectionMatcher.REEVALUATION_PAGE_SIZE, 0) } returns emptyList()
        coEvery { collectionDao.replaceMatches(COLLECTION_ID, emptyList()) } returns Unit

        val matchCount = matcher.reevaluateCollection(ACCOUNT_ID, COLLECTION_ID)

        assertEquals(0, matchCount)
        coVerify(exactly = 1) { collectionDao.replaceMatches(COLLECTION_ID, emptyList()) }
    }

    @Test
    fun reevaluateCollection_cancelledEmbeddingDoesNotReplacePreviousMembership() = runBlocking {
        val candidate = article("article-new", "Solar power investment")
        coEvery { collectionDao.getCollectionById(COLLECTION_ID) } returns collection()
        coEvery { articleDao.queryArticlesWithFeedPage(ACCOUNT_ID, SmartCollectionMatcher.REEVALUATION_PAGE_SIZE, 0) } returns listOf(candidate)
        coEvery { searchEngine.cacheEmbeddings(listOf(candidate)) } throws CancellationException("cancelled")

        runCatching { matcher.reevaluateCollection(ACCOUNT_ID, COLLECTION_ID) }

        coVerify(exactly = 0) { collectionDao.replaceMatches(any(), any()) }
    }

    private fun collection(query: String = "renewable energy") = SmartCollection(
        id = COLLECTION_ID,
        accountId = ACCOUNT_ID,
        name = "Energy",
        query = query,
    )

    private fun article(id: String, title: String): ArticleWithFeed {
        val article = Article(
            id = id,
            date = Date(),
            title = title,
            rawDescription = "",
            shortDescription = title,
            link = "https://example.com/$id",
            feedId = FEED_ID,
            accountId = ACCOUNT_ID,
        )
        val feed = Feed(
            id = FEED_ID,
            name = "Energy feed",
            url = "https://example.com/feed",
            groupId = "group-1",
            accountId = ACCOUNT_ID,
        )
        return ArticleWithFeed(article, feed)
    }

    private companion object {
        const val ACCOUNT_ID = 1
        const val COLLECTION_ID = "collection-energy"
        const val FEED_ID = "feed-energy"
        const val MATCH_SCORE = 0.76f
    }
}
