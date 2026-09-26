package com.mckimquyen.reader.infrastructure.ai.search

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleEmbeddingRecord
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleEmbeddingDao
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class SemanticSearchEngineTest {

    private lateinit var fakeDao: FakeArticleEmbeddingDao
    private lateinit var engine: SemanticSearchEngine

    private val feedTech = Feed(
        id = "feed_tech",
        name = "Tech Today",
        url = "https://tech.example.com",
        groupId = "group_1",
        accountId = 1,
    )
    private val feedFinance = Feed(
        id = "feed_finance",
        name = "Finance Daily",
        url = "https://finance.example.com",
        groupId = "group_1",
        accountId = 1,
    )

    /** In-memory stand-in for Room, with REPLACE semantics and call counters. */
    class FakeArticleEmbeddingDao(
        private val readDelayMs: Long = 0L,
    ) : ArticleEmbeddingDao {
        val storage = linkedMapOf<String, ArticleEmbeddingRecord>()
        var insertedRows = 0
        var insertCalls = 0
        var singleReadCalls = 0
        var bulkReadCalls = 0

        override suspend fun insertOrUpdateAll(records: List<ArticleEmbeddingRecord>) {
            insertCalls++
            insertedRows += records.size
            records.forEach { storage[it.articleId] = it }
        }

        override suspend fun getByArticleId(articleId: String): ArticleEmbeddingRecord? {
            singleReadCalls++
            return storage[articleId]
        }

        override suspend fun getByArticleIds(articleIds: List<String>): List<ArticleEmbeddingRecord> {
            bulkReadCalls++
            if (readDelayMs > 0) delay(readDelayMs)
            return articleIds.mapNotNull { storage[it] }
        }

        override suspend fun count(): Int = storage.size
    }

    @Before
    fun setUp() {
        fakeDao = FakeArticleEmbeddingDao()
        engine = SemanticSearchEngine(fakeDao)
    }

    private fun createArticle(
        id: String,
        title: String,
        description: String = "",
        feed: Feed = feedTech,
    ) = ArticleWithFeed(
        article = Article(
            id = id,
            title = title,
            rawDescription = description,
            shortDescription = description,
            link = "https://example.com/$id",
            feedId = feed.id,
            accountId = 1,
            date = Date(),
        ),
        feed = feed,
    )

    // ---- Embedding / scoring fundamentals ----

    @Test
    fun embed_producesUnitLengthVector() {
        val vector = engine.embed("Trí tuệ nhân tạo và mô hình học máy ngôn ngữ lớn")

        assertEquals(SemanticSearchEngine.EMBEDDING_DIM, vector.size)
        var sumSquares = 0f
        vector.forEach { sumSquares += it * it }
        assertEquals("Normalized vector length must be ~1.0", 1.0f, sumSquares, 0.01f)
    }

    @Test
    fun embed_emptyAndPunctuationOnlyText_returnsZeroVectorWithoutCrashing() {
        listOf("", "   ", "!!! ??? ...").forEach { text ->
            val vector = engine.embed(text)
            assertEquals(SemanticSearchEngine.EMBEDDING_DIM, vector.size)
            assertTrue("Degenerate text must produce a zero vector", vector.all { it == 0f })
        }
    }

    @Test
    fun embed_isDeterministicAcrossCalls() {
        val first = engine.embed("pin mặt trời và điện gió")
        val second = engine.embed("pin mặt trời và điện gió")
        assertArrayEqualsFloat(first, second)
    }

    @Test
    fun cosineSimilarity_identicalVectors_returns1f() {
        val vector = engine.embed("Công nghệ pin mặt trời quang điện")
        assertEquals(1.0f, engine.cosineSimilarity(vector, vector), 0.001f)
    }

    @Test
    fun cosineSimilarity_clampsToZeroAndHandlesMismatchedLengths() {
        assertEquals(0f, engine.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 0.001f)
        assertEquals(1f, engine.cosineSimilarity(floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 0f)), 0.001f)
        assertEquals(0f, engine.cosineSimilarity(FloatArray(0), floatArrayOf(1f)), 0.001f)
    }

    @Test
    fun tokenize_dropsStopWordsSingleCharsAndPunctuation() {
        val tokens = engine.tokenize("Pin, mặt trời và các hệ thống — A b")

        assertTrue(tokens.contains("pin"))
        assertTrue(tokens.contains("trời"))
        assertFalse("Stop words must be dropped", tokens.contains("và"))
        assertFalse("Stop words must be dropped", tokens.contains("các"))
        assertFalse("Single characters must be dropped", tokens.contains("a"))
        assertFalse(tokens.any { it.contains(",") || it.contains("—") })
    }

    @Test
    fun detectConcepts_identifiesMultipleCategoriesCorrectly() {
        val concepts = engine.detectConcepts(
            "Tesla phát triển xe điện và siêu máy tính AI tự hành tích hợp chip bán dẫn"
        )

        assertTrue(concepts.contains("ELECTRIC_VEHICLES"))
        assertTrue(concepts.contains("ARTIFICIAL_INTELLIGENCE"))
        assertTrue(concepts.contains("SEMICONDUCTOR"))
    }

    // ---- Ranking behaviour ----

    @Test
    fun rank_discoversConceptualMatches_withoutExactKeywordInTitle() = runBlocking {
        val cleanEnergyArt = createArticle(
            id = "art_solar",
            title = "Việt Nam lắp đặt thêm 500MW pin mặt trời và tuabin gió tại duyên hải Nam Trung Bộ",
            description = "Dự án nguồn điện tái tạo bổ sung công suất quang điện và phát triển điện gió ngoài khơi.",
        )
        val unrelatedArt = createArticle(
            id = "art_stock",
            title = "VN-Index biến động mạnh khi khối ngoại bán ròng cổ phiếu bất động sản",
            description = "Thị trường tài chính ghi nhận áp lực lạm phát và lãi suất tăng của các ngân hàng trung ương.",
            feed = feedFinance,
        )

        val results = engine.rank("năng lượng sạch", listOf(cleanEnergyArt, unrelatedArt))

        assertTrue("Should return at least 1 match", results.isNotEmpty())
        val top = results.first()
        assertEquals("art_solar", top.articleWithFeed.article.id)
        assertTrue("Semantic score should be >= 0.35f, got ${top.score}", top.score >= 0.35f)
        assertTrue(top.matchedConcepts.contains("CLEAN_ENERGY"))
        assertFalse(results.any { it.articleWithFeed.article.id == "art_stock" })
    }

    @Test
    fun rank_englishConcepts_matchesSemanticEquivalents() = runBlocking {
        val aiArticle = createArticle(
            id = "art_ai",
            title = "OpenAI releases new neural network transformer with deep learning capabilities",
            description = "Breakthrough architecture designed for generative artificial intelligence and code synthesis.",
        )
        val medicalArticle = createArticle(
            id = "art_med",
            title = "Clinical trials confirm antibody efficacy in phase 3 oncology study",
            description = "New pharmaceutical treatment shows strong promise across healthcare centers.",
            feed = feedFinance,
        )

        val results = engine.rank("machine learning and artificial intelligence", listOf(aiArticle, medicalArticle))

        assertTrue(results.isNotEmpty())
        val top = results.first()
        assertEquals("art_ai", top.articleWithFeed.article.id)
        assertTrue("Top score should be >= 0.40f, got ${top.score}", top.score >= 0.40f)
        assertTrue(top.matchedConcepts.contains("ARTIFICIAL_INTELLIGENCE"))
        if (results.size > 1) {
            assertTrue(
                "AI article must score clearly above the unrelated one",
                top.score > results[1].score + 0.15f,
            )
        }
    }

    @Test
    fun rank_resultsAreSortedByDescendingScore() = runBlocking {
        val articles = listOf(
            createArticle("art_1", "Pin mặt trời và điện gió tái tạo", "Năng lượng sạch quy mô lớn"),
            createArticle("art_2", "Quang điện cho hộ gia đình", "Điện mặt trời áp mái"),
            createArticle("art_3", "Xe điện và pin lithium", "Trạm sạc mở rộng"),
        )

        val results = engine.rank("năng lượng sạch pin mặt trời", articles)

        assertTrue(results.size >= 2)
        results.zipWithNext().forEach { (current, next) ->
            assertTrue("Results must be ordered by score", current.score >= next.score)
        }
    }

    @Test
    fun rank_respectsLimitAndThreshold() = runBlocking {
        val articles = (1..5).map { index ->
            createArticle("art_$index", "Pin mặt trời điện gió số $index", "Năng lượng tái tạo quang điện")
        }

        assertEquals(2, engine.rank("năng lượng sạch", articles, limit = 2).size)
        assertTrue(engine.rank("năng lượng sạch", articles, minScoreThreshold = 0.99f).isEmpty())
        assertTrue(engine.rank("năng lượng sạch", articles, limit = 0).isEmpty())
    }

    @Test
    fun rank_emptyQueryOrEmptyList_returnsEmpty() = runBlocking {
        val article = createArticle("1", "Tin tức công nghệ mới")

        assertTrue(engine.rank("", listOf(article)).isEmpty())
        assertTrue(engine.rank("   ", listOf(article)).isEmpty())
        assertTrue(engine.rank("AI", emptyList()).isEmpty())
    }

    @Test
    fun rank_blankQuery_touchesNeitherCacheNorDao() = runBlocking {
        engine.rank("   ", listOf(createArticle("art_1", "Pin mặt trời")))

        assertEquals(0, fakeDao.insertCalls)
        assertEquals(0, fakeDao.bulkReadCalls)
    }

    // ---- Persistent index (KNOW-05) ----

    @Test
    fun cacheEmbeddings_persistsVectorsAtExpectedDimension() = runBlocking {
        val articles = listOf(
            createArticle("art_1", "Trí tuệ nhân tạo thế hệ mới", "Mô hình ngôn ngữ lớn LLM"),
            createArticle("art_2", "Xe điện VinFast mở rộng thị trường", "Pin lithium và trạm sạc"),
        )

        val stats = engine.cacheEmbeddings(articles)

        assertEquals(2, stats.requested)
        assertEquals(2, stats.written)
        assertEquals(0, stats.hits)
        assertEquals(2, fakeDao.storage.size)
        val vector = fakeDao.storage.getValue("art_1").toFloatArray(SemanticSearchEngine.EMBEDDING_DIM)
        assertNotNull(vector)
        assertEquals(SemanticSearchEngine.EMBEDDING_DIM, vector!!.size)
    }

    @Test
    fun cacheEmbeddings_emptyList_isNoOp() = runBlocking {
        val stats = engine.cacheEmbeddings(emptyList())

        assertEquals(CacheUpdateStats.EMPTY, stats)
        assertEquals(0, fakeDao.insertCalls)
        assertEquals(0, fakeDao.bulkReadCalls)
    }

    @Test
    fun cacheEmbeddings_readsInOneBulkQuery_notOnePerArticle() = runBlocking {
        val articles = (1..50).map { createArticle("art_$it", "Bài viết số $it", "Nội dung mẫu $it") }

        engine.cacheEmbeddings(articles)

        assertEquals("Must not issue a per-article read", 0, fakeDao.singleReadCalls)
        assertEquals(1, fakeDao.bulkReadCalls)
        assertEquals(1, fakeDao.insertCalls)
    }

    @Test
    fun cacheEmbeddings_deduplicatesRepeatedArticleIds() = runBlocking {
        val duplicated = createArticle("art_dup", "Pin mặt trời", "Năng lượng tái tạo")

        val stats = engine.cacheEmbeddings(listOf(duplicated, duplicated, duplicated))

        assertEquals(1, stats.requested)
        assertEquals("Written count must match rows actually stored", 1, stats.written)
        assertEquals(1, fakeDao.insertedRows)
        assertEquals(1, fakeDao.storage.size)
    }

    @Test
    fun cacheEmbeddings_secondRun_isPureCacheHit() = runBlocking {
        val articles = listOf(
            createArticle("art_1", "Trí tuệ nhân tạo và học máy"),
            createArticle("art_2", "Điện mặt trời áp mái sạch"),
        )
        engine.cacheEmbeddings(articles)
        val insertsAfterFirst = fakeDao.insertedRows

        val stats = engine.cacheEmbeddings(articles)

        assertEquals(2, stats.hits)
        assertEquals(0, stats.written)
        assertEquals(0, stats.staleOrCorrupt)
        assertEquals(insertsAfterFirst, fakeDao.insertedRows)
    }

    @Test
    fun rank_secondKeystroke_reusesCachedEmbeddingsWithoutRewriting() = runBlocking {
        val articles = listOf(
            createArticle("art_1", "Trí tuệ nhân tạo và học máy"),
            createArticle("art_2", "Điện mặt trời áp mái sạch"),
        )
        engine.rank("AI", articles)
        val rowsAfterFirstKeystroke = fakeDao.insertedRows
        assertEquals(2, rowsAfterFirstKeystroke)

        engine.rank("học máy", articles)

        assertEquals(
            "A later keystroke must not recompute or rewrite embeddings",
            rowsAfterFirstKeystroke,
            fakeDao.insertedRows,
        )
    }

    @Test
    fun cacheEmbeddings_whenContentChanges_recomputesAndCountsAsStale() = runBlocking {
        engine.cacheEmbeddings(listOf(createArticle("art_1", "Phiên bản cũ", "Nội dung ban đầu")))
        val oldHash = fakeDao.storage.getValue("art_1").contentHash

        val stats = engine.cacheEmbeddings(
            listOf(createArticle("art_1", "Phiên bản mới đã sửa", "Nội dung thay đổi hoàn toàn"))
        )

        assertFalse("Hash must change when content changes", oldHash == fakeDao.storage.getValue("art_1").contentHash)
        assertEquals(1, stats.written)
        assertEquals(1, stats.staleOrCorrupt)
        assertEquals(0, stats.hits)
        assertEquals(1, fakeDao.storage.size)
    }

    @Test
    fun cacheEmbeddings_repairsCorruptOrWrongDimensionVectors() = runBlocking {
        val article = createArticle("art_1", "Pin mặt trời", "Năng lượng tái tạo")
        engine.cacheEmbeddings(listOf(article))
        val stored = fakeDao.storage.getValue("art_1")
        fakeDao.storage["art_1"] = stored.copy(embedding = "0.1,not-a-number,0.3")

        val stats = engine.cacheEmbeddings(listOf(article))

        assertEquals(1, stats.written)
        assertEquals(1, stats.staleOrCorrupt)
        assertNotNull(
            "Corrupt vector must be replaced by a valid one",
            fakeDao.storage.getValue("art_1").toFloatArray(SemanticSearchEngine.EMBEDDING_DIM),
        )
    }

    @Test
    fun rank_withCorruptCachedVector_stillReturnsCorrectTopResult() = runBlocking {
        val solar = createArticle(
            id = "art_solar",
            title = "Pin mặt trời và tuabin gió công suất lớn",
            description = "Nguồn điện tái tạo quang điện sạch.",
        )
        val finance = createArticle(
            id = "art_finance",
            title = "Cổ phiếu ngân hàng trung ương và lạm phát",
            description = "Thị trường chứng khoán biến động.",
            feed = feedFinance,
        )
        engine.cacheEmbeddings(listOf(solar, finance))
        fakeDao.storage["art_solar"] = fakeDao.storage.getValue("art_solar").copy(embedding = "broken")

        val results = engine.rank("năng lượng sạch", listOf(solar, finance))

        assertTrue(results.isNotEmpty())
        assertEquals("art_solar", results.first().articleWithFeed.article.id)
    }

    @Test
    fun concurrentCacheEmbeddings_writeEachArticleOnlyOnce() = runBlocking {
        val slowDao = FakeArticleEmbeddingDao(readDelayMs = 20L)
        val slowEngine = SemanticSearchEngine(slowDao)
        val articles = (1..5).map { createArticle("art_$it", "Bài viết $it", "Nội dung $it") }

        val stats = (1..4).map { async { slowEngine.cacheEmbeddings(articles) } }.awaitAll()

        assertEquals(5, slowDao.storage.size)
        assertEquals("Concurrent callers must not duplicate embedding work", 5, slowDao.insertedRows)
        assertEquals(5, stats.sumOf { it.written })
        assertEquals(15, stats.sumOf { it.hits })
    }

    // ---- Record serialization ----

    @Test
    fun articleEmbeddingRecord_roundTripSerialization() {
        val array = FloatArray(SemanticSearchEngine.EMBEDDING_DIM) { it * 0.1f }
        val record = ArticleEmbeddingRecord.fromFloatArray("test_art", "hash", array)

        val deserialized = record.toFloatArray(SemanticSearchEngine.EMBEDDING_DIM)

        assertNotNull(deserialized)
        assertArrayEqualsFloat(array, deserialized!!)
    }

    @Test
    fun articleEmbeddingRecord_rejectsWrongDimensionOrCorruptPayload() {
        val shortVector = ArticleEmbeddingRecord.fromFloatArray("a", "hash", FloatArray(8))
        assertNull(shortVector.toFloatArray(SemanticSearchEngine.EMBEDDING_DIM))

        val corrupt = ArticleEmbeddingRecord("a", "hash", "1.0,oops,3.0", 0L)
        assertNull(corrupt.toFloatArray(3))

        val empty = ArticleEmbeddingRecord("a", "hash", "", 0L)
        assertNull(empty.toFloatArray(SemanticSearchEngine.EMBEDDING_DIM))
    }

    @Test
    fun contentHash_isStableForSameContentAndDiffersOnChange() {
        val first = ArticleEmbeddingRecord.computeContentHash("Tiêu đề", "Mô tả ngắn")
        val same = ArticleEmbeddingRecord.computeContentHash("Tiêu đề", "Mô tả ngắn")
        val differentTitle = ArticleEmbeddingRecord.computeContentHash("Tiêu đề khác", "Mô tả ngắn")
        val differentBody = ArticleEmbeddingRecord.computeContentHash("Tiêu đề", "Mô tả khác")

        assertEquals(same, first)
        assertFalse(first == differentTitle)
        assertFalse(first == differentBody)
    }

    @Test
    fun contentHash_isNotConfusedByFieldBoundaryShifts() {
        val first = ArticleEmbeddingRecord.computeContentHash("AB", "C")
        val shifted = ArticleEmbeddingRecord.computeContentHash("A", "BC")

        assertFalse("Field boundary must not be ambiguous", first == shifted)
    }

    @Test
    fun contentHash_ignoresContentBeyondTruncationLimit() {
        val body = "x".repeat(ArticleEmbeddingRecord.MAX_DESC_CHARS)
        val first = ArticleEmbeddingRecord.computeContentHash("T", body)
        val longer = ArticleEmbeddingRecord.computeContentHash("T", body + "ignored tail")

        assertEquals("Only the embedded prefix affects the hash", first, longer)
    }

    private fun assertArrayEqualsFloat(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (index in expected.indices) {
            assertEquals(expected[index], actual[index], 0.0001f)
        }
    }
}
