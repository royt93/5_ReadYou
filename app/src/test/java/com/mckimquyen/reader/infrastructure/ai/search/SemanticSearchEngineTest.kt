package com.mckimquyen.reader.infrastructure.ai.search

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class SemanticSearchEngineTest {

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

    @Before
    fun setUp() {
        engine = SemanticSearchEngine()
    }

    private fun createArticle(
        id: String,
        title: String,
        description: String = "",
        feed: Feed = feedTech,
    ): ArticleWithFeed {
        return ArticleWithFeed(
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
    }

    @Test
    fun embed_producesUnitLengthVector() {
        val text = "Trí tuệ nhân tạo và mô hình học máy ngôn ngữ lớn"
        val vec = engine.embed(text)

        assertEquals(SemanticSearchEngine.EMBEDDING_DIM, vec.size)

        var sumSquares = 0f
        for (v in vec) {
            sumSquares += v * v
        }
        assertEquals("Normalized vector length must be ~1.0", 1.0f, sumSquares, 0.01f)
    }

    @Test
    fun cosineSimilarity_identicalVectors_returns1f() {
        val vec = engine.embed("Công nghệ pin mặt trời quang điện")
        val sim = engine.cosineSimilarity(vec, vec)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun rank_discoversConceptualMatches_withoutExactKeywordInTitle() {
        // Query user enters: "năng lượng sạch"
        // Target article title has NO words matching "năng lượng sạch", but talks about "pin mặt trời" and "tuabin gió"
        val cleanEnergyArt = createArticle(
            id = "art_solar",
            title = "Việt Nam lắp đặt thêm 500MW pin mặt trời và tuabin gió tại duyên hải Nam Trung Bộ",
            description = "Dự án nguồn điện tái tạo bổ sung công suất quang điện và phát triển điện gió ngoài khơi.",
            feed = feedTech,
        )
        val unrelatedArt = createArticle(
            id = "art_stock",
            title = "VN-Index biến động mạnh khi khối ngoại bán ròng cổ phiếu bất động sản",
            description = "Thị trường tài chính ghi nhận áp lực lạm phát và lãi suất tăng của các ngân hàng trung ương.",
            feed = feedFinance,
        )

        val results = engine.rank(
            query = "năng lượng sạch",
            articles = listOf(cleanEnergyArt, unrelatedArt),
        )

        assertTrue("Should return at least 1 match", results.isNotEmpty())
        val topResult = results.first()
        assertEquals("art_solar", topResult.articleWithFeed.article.id)
        assertTrue("Semantic score should be >= 0.35f, got ${topResult.score}", topResult.score >= 0.35f)
        assertTrue("Should detect CLEAN_ENERGY concept", topResult.matchedConcepts.contains("CLEAN_ENERGY"))

        assertFalse("Unrelated finance article should not match clean energy query",
            results.any { it.articleWithFeed.article.id == "art_stock" })
    }

    @Test
    fun rank_englishConcepts_matchesSemanticEquivalents() {
        val aiArticle = createArticle(
            id = "art_ai",
            title = "OpenAI releases new neural network transformer with deep learning capabilities",
            description = "Breakthrough architecture designed for generative artificial intelligence and code synthesis.",
            feed = feedTech,
        )
        val medicalArticle = createArticle(
            id = "art_med",
            title = "Clinical trials confirm antibody efficacy in phase 3 oncology study",
            description = "New pharmaceutical treatment shows strong promise across healthcare centers.",
            feed = feedFinance,
        )

        val results = engine.rank(
            query = "machine learning and artificial intelligence",
            articles = listOf(aiArticle, medicalArticle),
        )

        assertTrue("Results should not be empty", results.isNotEmpty())
        val topResult = results.first()
        assertEquals("art_ai", topResult.articleWithFeed.article.id)
        assertTrue("Top result score should be >= 0.40f, got ${topResult.score}", topResult.score >= 0.40f)
        assertTrue("Must detect ARTIFICIAL_INTELLIGENCE concept", topResult.matchedConcepts.contains("ARTIFICIAL_INTELLIGENCE"))
        if (results.size > 1) {
            assertTrue("AI article must score significantly higher than unrelated article",
                topResult.score > results[1].score + 0.15f)
        }
    }

    @Test
    fun rank_emptyQueryOrEmptyList_returnsEmpty() {
        val art = createArticle("1", "Tin tức công nghệ mới")
        assertTrue(engine.rank("", listOf(art)).isEmpty())
        assertTrue(engine.rank("   ", listOf(art)).isEmpty())
        assertTrue(engine.rank("AI", emptyList()).isEmpty())
    }

    @Test
    fun detectConcepts_identifiesMultipleCategoriesCorrectly() {
        val text = "Tesla phát triển xe điện và siêu máy tính AI tự hành tích hợp chip bán dẫn"
        val concepts = engine.detectConcepts(text)

        assertTrue("Should detect ELECTRIC_VEHICLES", concepts.contains("ELECTRIC_VEHICLES"))
        assertTrue("Should detect ARTIFICIAL_INTELLIGENCE", concepts.contains("ARTIFICIAL_INTELLIGENCE"))
        assertTrue("Should detect SEMICONDUCTOR", concepts.contains("SEMICONDUCTOR"))
    }
}
