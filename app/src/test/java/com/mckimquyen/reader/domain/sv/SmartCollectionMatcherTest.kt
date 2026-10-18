package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionArticle
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import com.mckimquyen.reader.infrastructure.ai.search.CacheUpdateStats
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class SmartCollectionMatcherTest {

    private val smartCollectionDao: SmartCollectionDao = mockk(relaxed = true)
    private val semanticSearchEngine: SemanticSearchEngine = mockk(relaxed = true)

    private lateinit var matcher: SmartCollectionMatcher

    @Before
    fun setUp() {
        matcher = SmartCollectionMatcher(
            smartCollectionDao = smartCollectionDao,
            semanticSearchEngine = semanticSearchEngine,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun matchNewArticles_emptyCandidates_returnsZeroWithoutQueryingDb() = runBlocking {
        val result = matcher.matchNewArticles(ACCOUNT_ID, emptyList())

        assertEquals(0, result)
        coVerify(exactly = 0) { smartCollectionDao.getCollectionsByAccount(any()) }
    }

    @Test
    fun matchNewArticles_noCollections_returnsZeroWithoutRunningRank() = runBlocking {
        coEvery { smartCollectionDao.getCollectionsByAccount(ACCOUNT_ID) } returns emptyList()

        val candidate = makeArticle("art-1", "AI breakthrough")
        val result = matcher.matchNewArticles(ACCOUNT_ID, listOf(candidate))

        assertEquals(0, result)
        coVerify(exactly = 0) { semanticSearchEngine.rank(any(), any(), any(), any()) }
    }

    @Test
    fun matchNewArticles_matchingArticles_insertsJunctionRecords() = runBlocking {
        val col = SmartCollection(
            id = "col-ai",
            accountId = ACCOUNT_ID,
            name = "AI News",
            query = "artificial intelligence",
            minScoreThreshold = 0.22f,
        )
        coEvery { smartCollectionDao.getCollectionsByAccount(ACCOUNT_ID) } returns listOf(col)
        coEvery { semanticSearchEngine.cacheEmbeddings(any()) } returns CacheUpdateStats.EMPTY

        val candidate = makeArticle("art-1", "Transformer model news")
        coEvery {
            semanticSearchEngine.rank(col.query, listOf(candidate), col.minScoreThreshold, any())
        } returns listOf(
            SemanticSearchResult(candidate, score = 0.65f, matchedConcepts = listOf("ARTIFICIAL_INTELLIGENCE"))
        )

        val insertedSlots = slot<List<SmartCollectionArticle>>()
        coEvery { smartCollectionDao.insertMatches(capture(insertedSlots)) } returns listOf(1L)

        val result = matcher.matchNewArticles(ACCOUNT_ID, listOf(candidate))

        assertEquals(1, result)
        assertEquals(1, insertedSlots.captured.size)
        val match = insertedSlots.captured[0]
        assertEquals("col-ai", match.collectionId)
        assertEquals("art-1", match.articleId)
        assertEquals(0.65f, match.score, 0.001f)
    }

    @Test
    fun matchNewArticles_noArticlesAboveThreshold_insertsNothing() = runBlocking {
        val col = SmartCollection(
            id = "col-ai",
            accountId = ACCOUNT_ID,
            name = "AI News",
            query = "artificial intelligence",
            minScoreThreshold = 0.22f,
        )
        coEvery { smartCollectionDao.getCollectionsByAccount(ACCOUNT_ID) } returns listOf(col)
        coEvery { semanticSearchEngine.cacheEmbeddings(any()) } returns CacheUpdateStats.EMPTY

        val candidate = makeArticle("art-cooking", "How to bake a cake")
        coEvery {
            semanticSearchEngine.rank(col.query, listOf(candidate), col.minScoreThreshold, any())
        } returns emptyList()

        val result = matcher.matchNewArticles(ACCOUNT_ID, listOf(candidate))

        assertEquals(0, result)
        coVerify(exactly = 0) { smartCollectionDao.insertMatches(any()) }
    }

    private fun makeArticle(id: String, title: String): ArticleWithFeed {
        val article = Article(
            id = id,
            date = Date(),
            title = title,
            rawDescription = "",
            shortDescription = title,
            link = "https://example.com/$id",
            feedId = "feed-1",
            accountId = ACCOUNT_ID,
        )
        val feed = Feed(
            id = "feed-1",
            name = "Tech Feed",
            url = "https://example.com/rss",
            groupId = "group-1",
            accountId = ACCOUNT_ID,
        )
        return ArticleWithFeed(article, feed)
    }

    private companion object {
        const val ACCOUNT_ID = 1
    }
}
