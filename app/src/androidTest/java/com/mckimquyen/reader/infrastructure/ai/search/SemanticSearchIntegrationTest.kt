package com.mckimquyen.reader.infrastructure.ai.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleEmbeddingRecord
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.component.search.SemanticSearchCard
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SemanticSearchIntegrationTest {

    private lateinit var database: AndroidDatabase
    private lateinit var engine: SemanticSearchEngine
    private lateinit var indexer: SemanticEmbeddingIndexer

    private val account = Account(
        id = ACCOUNT_ID,
        name = "Test account",
        type = AccountType.Local,
    )
    private val group = Group(id = "group_1", name = "News", accountId = ACCOUNT_ID)

    private val feedNews = Feed(
        id = "feed_news",
        name = "VnExpress",
        url = "https://vnexpress.net/rss",
        groupId = group.id,
        accountId = ACCOUNT_ID,
    )
    private val feedTech = Feed(
        id = "feed_tech",
        name = "TechCrunch",
        url = "https://techcrunch.com/rss",
        groupId = group.id,
        accountId = ACCOUNT_ID,
    )
    private val feedFinance = Feed(
        id = "feed_finance",
        name = "Bloomberg",
        url = "https://bloomberg.com/rss",
        groupId = group.id,
        accountId = ACCOUNT_ID,
    )

    private val cleanEnergyArticle = article(
        id = "art_energy",
        feedId = feedNews.id,
        title = "Phát động dự án đại công trình điện gió ngoài khơi và pin mặt trời tại duyên hải",
        description = "Hệ thống nguồn điện tái tạo bổ sung công suất quang điện sạch quy mô quốc gia.",
    )
    private val aiArticle = article(
        id = "art_ai",
        feedId = feedTech.id,
        title = "OpenAI chính thức trình làng mô hình trí tuệ nhân tạo GPT-5 đa phương thức",
        description = "Đột phá về mạng nơron transformer và học sâu machine learning giúp giải toán siêu việt.",
    )
    private val financeArticle = article(
        id = "art_finance",
        feedId = feedFinance.id,
        title = "Thị trường chứng khoán khởi sắc khi ngân hàng trung ương hạ lãi suất điều hành",
        description = "Dòng tiền đổ vào cổ phiếu bất động sản và giảm áp lực lạm phát ngắn hạn.",
    )

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java,
        ).build()
        engine = SemanticSearchEngine(database.articleEmbeddingDao())
        indexer = SemanticEmbeddingIndexer(database.articleDao(), engine)

        database.accountDao().insert(account)
        database.groupDao().insert(group)
        database.feedDao().insertList(listOf(feedNews, feedTech, feedFinance))
        database.articleDao().insertList(
            listOf(cleanEnergyArticle.article, aiArticle.article, financeArticle.article),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingArticle_cascadesAndRemovesItsEmbedding() = runBlocking {
        indexer.indexRecent(ACCOUNT_ID)
        assertEquals(3, database.articleEmbeddingDao().count())

        database.articleDao().deleteByFeedId(ACCOUNT_ID, feedFinance.id)

        assertNull(database.articleEmbeddingDao().getByArticleId(financeArticle.article.id))
        assertEquals(2, database.articleEmbeddingDao().count())
    }

    @Test
    fun indexRecent_populatesIndexBeforeAnySearchHappens() = runBlocking {
        val stats = indexer.indexRecent(ACCOUNT_ID)

        assertEquals(3, stats.requested)
        assertEquals(3, stats.written)
        assertEquals(0, stats.hits)
        assertEquals(3, database.articleEmbeddingDao().count())

        val storedVector = database.articleEmbeddingDao()
            .getByArticleId(aiArticle.article.id)
            ?.toFloatArray(SemanticSearchEngine.EMBEDDING_DIM)
        assertNotNull("Stored vector must deserialize at the expected dimension", storedVector)
        assertEquals(SemanticSearchEngine.EMBEDDING_DIM, storedVector!!.size)
    }

    @Test
    fun indexRecent_isIdempotent_whenContentUnchanged() = runBlocking {
        indexer.indexRecent(ACCOUNT_ID)
        val second = indexer.indexRecent(ACCOUNT_ID)

        assertEquals(3, second.hits)
        assertEquals(0, second.written)
        assertEquals(0, second.staleOrCorrupt)
    }

    @Test
    fun rank_afterIndexing_writesNothingAndStillRanksConceptually() = runBlocking {
        indexer.indexRecent(ACCOUNT_ID)
        val articles = database.articleDao().queryRecentArticlesWithFeed(ACCOUNT_ID, 200)

        val energyMatches = engine.rank("công nghệ năng lượng sạch và phát triển bền vững", articles)
        val topEnergy = energyMatches.firstOrNull()
        assertNotNull("Clean energy query must match", topEnergy)
        assertEquals("art_energy", topEnergy!!.articleWithFeed.article.id)
        assertTrue("Top energy score too low: ${topEnergy.score}", topEnergy.score >= 0.40f)
        assertTrue(topEnergy.matchedConcepts.contains("CLEAN_ENERGY"))

        val aiMatches = engine.rank("mô hình ngôn ngữ lớn và học sâu", articles)
        val topAi = aiMatches.firstOrNull()
        assertNotNull("AI query must match", topAi)
        assertEquals("art_ai", topAi!!.articleWithFeed.article.id)
        assertTrue(topAi.matchedConcepts.contains("ARTIFICIAL_INTELLIGENCE"))

        // Searching must not rewrite the index once it is warm.
        val afterSearch = engine.cacheEmbeddings(articles)
        assertEquals(0, afterSearch.written)
        assertEquals(articles.size, afterSearch.hits)
    }

    @Test
    fun rank_repeatedKeystrokes_areFasterOnWarmIndexThanColdStart() = runBlocking {
        val articles = database.articleDao().queryRecentArticlesWithFeed(ACCOUNT_ID, 200)

        val coldNanos = measureNanos { engine.rank("năng lượng", articles) }
        val warmNanos = (1..5).minOf { index -> measureNanos { engine.rank("năng lượng $index", articles) } }

        assertTrue(
            "Warm search ($warmNanos ns) should not be slower than cold start ($coldNanos ns)",
            warmNanos <= coldNanos,
        )
    }

    @Test
    fun changedArticleContent_invalidatesStaleVectorOnNextIndexRun() = runBlocking {
        indexer.indexRecent(ACCOUNT_ID)
        val originalHash = database.articleEmbeddingDao()
            .getByArticleId(aiArticle.article.id)!!.contentHash

        database.articleDao().update(
            aiArticle.article.copy(
                title = "Tiêu đề hoàn toàn mới về xe điện và pin lithium",
                shortDescription = "Nội dung đã thay đổi sang lĩnh vực xe điện.",
            )
        )
        val stats = indexer.indexRecent(ACCOUNT_ID)

        val updatedHash = database.articleEmbeddingDao()
            .getByArticleId(aiArticle.article.id)!!.contentHash
        assertFalse("Hash must change when content changes", originalHash == updatedHash)
        assertEquals(1, stats.written)
        assertEquals(1, stats.staleOrCorrupt)
        assertEquals(2, stats.hits)
    }

    @Test
    fun corruptStoredVector_isRepairedInsteadOfCrashingSearch() = runBlocking {
        indexer.indexRecent(ACCOUNT_ID)
        val corrupted = database.articleEmbeddingDao().getByArticleId(aiArticle.article.id)!!
        database.articleEmbeddingDao().insertOrUpdateAll(listOf(corrupted.copy(embedding = "not,a,vector")))

        val stats = indexer.indexRecent(ACCOUNT_ID)

        assertEquals(1, stats.written)
        assertEquals(1, stats.staleOrCorrupt)
        val repaired = database.articleEmbeddingDao()
            .getByArticleId(aiArticle.article.id)!!
            .toFloatArray(SemanticSearchEngine.EMBEDDING_DIM)
        assertNotNull("Corrupt vector must be recomputed", repaired)
    }

    @Test
    fun semanticSearchCard_rendersTopResultOnDevice() = runBlocking {
        indexer.indexRecent(ACCOUNT_ID)
        val articles = database.articleDao().queryRecentArticlesWithFeed(ACCOUNT_ID, 200)
        val topResult = engine.rank("năng lượng sạch", articles).first()

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent { SemanticSearchCard(result = topResult, onClick = {}) }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach cleanly on live Android runtime", composeView)
        }
        scenario.close()
    }

    private inline fun measureNanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun article(
        id: String,
        feedId: String,
        title: String,
        description: String,
    ) = ArticleWithFeed(
        article = Article(
            id = id,
            title = title,
            rawDescription = description,
            shortDescription = description,
            link = "https://example.com/$id",
            feedId = feedId,
            accountId = ACCOUNT_ID,
            date = Date(),
        ),
        feed = Feed(
            id = feedId,
            name = feedId,
            url = "https://example.com/$feedId",
            groupId = "group_1",
            accountId = ACCOUNT_ID,
        ),
    )

    private companion object {
        const val ACCOUNT_ID = 1
    }
}
