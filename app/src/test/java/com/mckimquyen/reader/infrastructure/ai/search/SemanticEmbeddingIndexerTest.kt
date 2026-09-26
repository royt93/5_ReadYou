package com.mckimquyen.reader.infrastructure.ai.search

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class SemanticEmbeddingIndexerTest {

    private lateinit var articleDao: ArticleDao
    private lateinit var fakeEmbeddingDao: SemanticSearchEngineTest.FakeArticleEmbeddingDao
    private lateinit var engine: SemanticSearchEngine
    private lateinit var indexer: SemanticEmbeddingIndexer

    private val feed = Feed(
        id = "feed_1",
        name = "Feed",
        url = "https://example.com/rss",
        groupId = "group_1",
        accountId = ACCOUNT_ID,
    )

    @Before
    fun setUp() {
        articleDao = mockk()
        fakeEmbeddingDao = SemanticSearchEngineTest.FakeArticleEmbeddingDao()
        engine = SemanticSearchEngine(fakeEmbeddingDao)
        indexer = SemanticEmbeddingIndexer(articleDao, engine)
    }

    private fun article(id: String) = ArticleWithFeed(
        article = Article(
            id = id,
            title = "Bài viết $id về pin mặt trời",
            rawDescription = "Năng lượng tái tạo quang điện $id",
            shortDescription = "Năng lượng tái tạo quang điện $id",
            link = "https://example.com/$id",
            feedId = feed.id,
            accountId = ACCOUNT_ID,
            date = Date(),
        ),
        feed = feed,
    )

    @Test
    fun indexRecent_embedsEveryRecentArticleOnce() = runBlocking {
        val articles = (1..3).map { article("art_$it") }
        coEvery {
            articleDao.queryRecentArticlesWithFeed(ACCOUNT_ID, SemanticEmbeddingIndexer.DEFAULT_INDEX_LIMIT)
        } returns articles

        val stats = indexer.indexRecent(ACCOUNT_ID)

        assertEquals(3, stats.requested)
        assertEquals(3, stats.written)
        assertEquals(0, stats.hits)
        assertEquals(3, fakeEmbeddingDao.storage.size)
    }

    @Test
    fun indexRecent_runAgain_isPureCacheHit() = runBlocking {
        val articles = (1..3).map { article("art_$it") }
        coEvery {
            articleDao.queryRecentArticlesWithFeed(ACCOUNT_ID, SemanticEmbeddingIndexer.DEFAULT_INDEX_LIMIT)
        } returns articles
        indexer.indexRecent(ACCOUNT_ID)
        val rowsAfterFirstRun = fakeEmbeddingDao.insertedRows

        val stats = indexer.indexRecent(ACCOUNT_ID)

        assertEquals(3, stats.hits)
        assertEquals(0, stats.written)
        assertEquals(rowsAfterFirstRun, fakeEmbeddingDao.insertedRows)
    }

    @Test
    fun indexRecent_noArticles_isNoOp() = runBlocking {
        coEvery {
            articleDao.queryRecentArticlesWithFeed(ACCOUNT_ID, SemanticEmbeddingIndexer.DEFAULT_INDEX_LIMIT)
        } returns emptyList()

        val stats = indexer.indexRecent(ACCOUNT_ID)

        assertEquals(CacheUpdateStats.EMPTY, stats)
        assertEquals(0, fakeEmbeddingDao.insertCalls)
    }

    @Test
    fun indexRecent_nonPositiveLimit_skipsTheQueryEntirely() = runBlocking {
        val stats = indexer.indexRecent(ACCOUNT_ID, limit = 0)

        assertEquals(CacheUpdateStats.EMPTY, stats)
        coVerify(exactly = 0) { articleDao.queryRecentArticlesWithFeed(any(), any()) }
    }

    @Test
    fun indexRecent_honoursExplicitLimit() = runBlocking {
        coEvery { articleDao.queryRecentArticlesWithFeed(ACCOUNT_ID, 2) } returns listOf(article("a"), article("b"))

        val stats = indexer.indexRecent(ACCOUNT_ID, limit = 2)

        assertEquals(2, stats.requested)
        coVerify(exactly = 1) { articleDao.queryRecentArticlesWithFeed(ACCOUNT_ID, 2) }
    }

    private companion object {
        const val ACCOUNT_ID = 1
    }
}
