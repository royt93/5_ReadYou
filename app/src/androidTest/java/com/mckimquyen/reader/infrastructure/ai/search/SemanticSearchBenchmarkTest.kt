package com.mckimquyen.reader.infrastructure.ai.search

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Measures the KNOW-05 acceptance criterion on a real file-backed Room database at the same
 * candidate volume `HomeViewModel` uses (200 articles), so the recorded numbers reflect device I/O
 * rather than an in-memory shortcut.
 */
@RunWith(AndroidJUnit4::class)
class SemanticSearchBenchmarkTest {

    private lateinit var database: AndroidDatabase
    private lateinit var engine: SemanticSearchEngine
    private lateinit var indexer: SemanticEmbeddingIndexer

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(DB_NAME)
        database = Room.databaseBuilder(context, AndroidDatabase::class.java, DB_NAME).build()
        engine = SemanticSearchEngine(database.articleEmbeddingDao())
        indexer = SemanticEmbeddingIndexer(database.articleDao(), engine)

        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "bench", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "bench", accountId = ACCOUNT_ID))
        database.feedDao().insertList(
            listOf(
                Feed(
                    id = FEED_ID,
                    name = "Bench feed",
                    url = "https://example.com/rss",
                    groupId = GROUP_ID,
                    accountId = ACCOUNT_ID,
                )
            )
        )
        database.articleDao().insertList((1..ARTICLE_COUNT).map(::benchmarkArticle))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun searchOn200Articles_isFasterAfterIndexingAndStaysUnderBudget() = runBlocking {
        val candidates = database.articleDao().queryRecentArticlesWithFeed(ACCOUNT_ID, ARTICLE_COUNT)
        assertEquals(ARTICLE_COUNT, candidates.size)

        // Cold: nothing indexed yet, so this call also builds all 200 vectors.
        val coldMs = measureMillis { engine.rank("năng lượng sạch", candidates) }

        // Warm: repeated keystrokes against an index that is already populated.
        val warmSamples = KEYSTROKES.map { query ->
            measureMillis { engine.rank(query, candidates) }
        }
        val warmMedianMs = warmSamples.sorted()[warmSamples.size / 2]

        Log.i(
            TAG,
            "KNOW-05 benchmark n=$ARTICLE_COUNT coldMs=$coldMs warmSamplesMs=$warmSamples warmMedianMs=$warmMedianMs",
        )

        assertTrue(
            "Warm keystroke ($warmMedianMs ms) must not be slower than cold start ($coldMs ms)",
            warmMedianMs <= coldMs,
        )
        assertTrue(
            "Warm keystroke must stay interactive: ${warmMedianMs}ms exceeded ${WARM_BUDGET_MS}ms",
            warmMedianMs <= WARM_BUDGET_MS,
        )
    }

    @Test
    fun indexingRunOnceAtSyncTime_makesLaterIndexRunsPureCacheHits() = runBlocking {
        val first = indexer.indexRecent(ACCOUNT_ID, limit = ARTICLE_COUNT)
        assertEquals(ARTICLE_COUNT, first.written)

        val second = indexer.indexRecent(ACCOUNT_ID, limit = ARTICLE_COUNT)

        assertEquals("A second sync must not recompute anything", 0, second.written)
        assertEquals(ARTICLE_COUNT, second.hits)
        assertEquals(ARTICLE_COUNT, database.articleEmbeddingDao().count())
    }

    @Test
    fun searchAfterSyncIndexing_writesNothingOnTheKeystrokePath() = runBlocking {
        indexer.indexRecent(ACCOUNT_ID, limit = ARTICLE_COUNT)
        val candidates = database.articleDao().queryRecentArticlesWithFeed(ACCOUNT_ID, ARTICLE_COUNT)

        val results = engine.rank("pin mặt trời", candidates)
        val statsDuringSearch = engine.cacheEmbeddings(candidates)

        assertTrue("Search must still return results", results.isNotEmpty())
        assertEquals("Warm search must not rewrite the index", 0, statsDuringSearch.written)
        assertEquals(ARTICLE_COUNT, statsDuringSearch.hits)
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun benchmarkArticle(index: Int): Article {
        val topic = TOPICS[index % TOPICS.size]
        return Article(
            id = "bench_$index",
            title = "$topic số $index",
            rawDescription = "$topic mô tả chi tiết số $index với nhiều nội dung nền.",
            shortDescription = "$topic mô tả chi tiết số $index với nhiều nội dung nền.",
            link = "https://example.com/bench_$index",
            feedId = FEED_ID,
            accountId = ACCOUNT_ID,
            date = Date(System.currentTimeMillis() - index * 1000L),
        )
    }

    private companion object {
        const val TAG = "SemanticBenchmark"
        const val DB_NAME = "semantic_benchmark_db"
        const val ACCOUNT_ID = 1
        const val GROUP_ID = "bench_group"
        const val FEED_ID = "bench_feed"
        const val ARTICLE_COUNT = 200
        const val WARM_BUDGET_MS = 250L

        val KEYSTROKES = listOf("n", "nă", "năng", "năng l", "năng lư", "năng lượng", "năng lượng sạch")

        val TOPICS = listOf(
            "Pin mặt trời và điện gió tái tạo",
            "Trí tuệ nhân tạo và mô hình ngôn ngữ",
            "Chíp bán dẫn và vi mạch",
            "Thị trường chứng khoán và lãi suất",
            "Xe điện và pin lithium",
        )
    }
}
