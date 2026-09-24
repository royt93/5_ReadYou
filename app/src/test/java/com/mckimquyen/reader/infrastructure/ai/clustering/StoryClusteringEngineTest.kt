package com.mckimquyen.reader.infrastructure.ai.clustering

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class StoryClusteringEngineTest {

    private lateinit var engine: StoryClusteringEngine

    private val feedVnExpress = Feed(id = "feed_vnexpress", name = "VnExpress", url = "https://vnexpress.net/rss", groupId = "group_1", accountId = 1)
    private val feedTuoiTre = Feed(id = "feed_tuoitre", name = "Tuổi Trẻ", url = "https://tuoitre.vn/rss", groupId = "group_1", accountId = 1)
    private val feedBBC = Feed(id = "feed_bbc", name = "BBC News", url = "https://bbc.com/rss", groupId = "group_1", accountId = 1)
    private val feedTechCrunch = Feed(id = "feed_techcrunch", name = "TechCrunch", url = "https://techcrunch.com/rss", groupId = "group_1", accountId = 1)

    @Before
    fun setUp() {
        engine = StoryClusteringEngine()
        engine.clearCache()
    }

    private fun createArticle(
        id: String,
        title: String,
        description: String = "",
        feed: Feed,
        date: Date = Date(),
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
                date = date,
            ),
            feed = feed,
        )
    }

    @Test
    fun calculateSimilarity_identicalTitles_returns1f() {
        val a1 = createArticle("1", "Apple chính thức công bố iPhone 16", feed = feedVnExpress)
        val a2 = createArticle("2", "Apple chính thức công bố iPhone 16", feed = feedTuoiTre)

        val sim = engine.calculateSimilarity(a1, a2)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun calculateSimilarity_vietnameseBreakingNewsArticles_returnsHighSimilarity() {
        val a1 = createArticle(
            id = "1",
            title = "Apple ra mắt dòng iPhone 16 Pro Max với chip A18",
            description = "Sự kiện rạng sáng nay Apple đã giới thiệu thế hệ iPhone 16 Pro Max cao cấp.",
            feed = feedVnExpress,
        )
        val a2 = createArticle(
            id = "2",
            title = "Apple công bố iPhone 16 Pro Max tích hợp chip A18",
            description = "Toàn cảnh lễ ra mắt sản phẩm mới iPhone 16 Pro Max trang bị vi xử lý Apple A18.",
            feed = feedTuoiTre,
        )

        val sim = engine.calculateSimilarity(a1, a2)
        assertTrue("Similarity should be >= 0.55 for duplicate breaking news, got $sim", sim >= 0.55f)
    }

    @Test
    fun calculateSimilarity_differentTopics_returnsLowSimilarity() {
        val a1 = createArticle(
            id = "1",
            title = "Apple ra mắt iPhone 16 Pro Max tại trụ sở Cupertino",
            feed = feedVnExpress,
        )
        val a2 = createArticle(
            id = "2",
            title = "Giá vàng thế giới hôm nay sụt giảm kỷ lục",
            feed = feedTuoiTre,
        )

        val sim = engine.calculateSimilarity(a1, a2)
        assertTrue("Similarity between unrelated topics should be low, got $sim", sim < 0.25f)
    }

    @Test
    fun tokenize_filtersStopwordsAndPunctuation() {
        val text = "Và đây là chiếc iPhone 16 mới của Apple, vừa ra mắt hôm nay!"
        val tokens = engine.tokenize(text)

        assertTrue(tokens.contains("iphone"))
        assertTrue(tokens.contains("16"))
        assertTrue(tokens.contains("apple"))
        assertFalse("Stopword 'và' should be removed", tokens.contains("và"))
        assertFalse("Stopword 'của' should be removed", tokens.contains("của"))
        assertFalse("Stopword 'hôm' should be removed", tokens.contains("hôm"))
    }

    @Test
    fun cluster_groupsMultipleSourcesIntoSingleCluster() {
        val now = System.currentTimeMillis()
        val a1 = createArticle(
            id = "a1",
            title = "Thủ tướng phát lệnh khởi công tuyến đường sắt cao tốc Bắc Nam",
            description = "Dự án giao thông thế kỷ chính thức được triển khai tại Hà Nội.",
            feed = feedVnExpress,
            date = Date(now),
        )
        val a2 = createArticle(
            id = "a2",
            title = "Chính thức khởi công đường sắt cao tốc Bắc Nam với quy mô lớn",
            description = "Lễ khởi công đường sắt cao tốc Bắc Nam diễn ra trọng thể với sự tham gia của Thủ tướng.",
            feed = feedTuoiTre,
            date = Date(now - TimeUnit.HOURS.toMillis(2)),
        )
        val a3 = createArticle(
            id = "a3",
            title = "Vietnam launches landmark North-South high-speed railway project",
            description = "Prime Minister officially orders construction of nationwide high-speed rail line.",
            feed = feedBBC,
            date = Date(now - TimeUnit.HOURS.toMillis(4)),
        )
        val unrelated = createArticle(
            id = "a4",
            title = "Bão số 3 đổ bộ vào đất liền gây mưa lớn diện rộng",
            feed = feedVnExpress,
            date = Date(now),
        )

        val result = engine.cluster(listOf(a1, a2, unrelated))

        assertEquals("Should form exactly 1 cluster for the high-speed rail story", 1, result.clusters.size)
        val cluster = result.clusters.first()
        assertEquals(2, cluster.articleCount)
        assertEquals(2, cluster.sourceCount)
        assertTrue(cluster.isMultiSource)

        // Verifying mapping
        assertTrue(result.leadClusterMap.containsKey(cluster.leadArticle.article.id))
        assertEquals(1, result.nonLeadIds.size)
        assertFalse("Unrelated article should not be in nonLeadIds", result.nonLeadIds.contains(unrelated.article.id))
    }

    @Test
    fun cluster_computesRealSimilarityScore_insteadOfHardcoded085f() {
        val now = System.currentTimeMillis()
        val a1 = createArticle(
            id = "a1",
            title = "Apple ra mắt dòng iPhone 16 Pro Max với chip A18",
            description = "Sự kiện rạng sáng nay Apple đã giới thiệu thế hệ iPhone 16 Pro Max cao cấp.",
            feed = feedVnExpress,
            date = Date(now),
        )
        val a2 = createArticle(
            id = "a2",
            title = "Apple công bố iPhone 16 Pro Max tích hợp chip A18",
            description = "Toàn cảnh lễ ra mắt sản phẩm mới iPhone 16 Pro Max trang bị vi xử lý Apple A18.",
            feed = feedTuoiTre,
            date = Date(now - TimeUnit.HOURS.toMillis(1)),
        )

        val result = engine.cluster(listOf(a1, a2))
        assertEquals(1, result.clusters.size)

        val cluster = result.clusters.first()
        val actualPairSim = engine.calculateSimilarity(a1, a2)

        // Phải phản ánh đúng điểm tính toán thực tế, không bị gán chết 0.85f
        assertEquals("similarityScore must match actual calculated similarity", actualPairSim, cluster.similarityScore, 0.001f)
        assertTrue("similarityScore should be >= threshold", cluster.similarityScore >= 0.45f)
    }

    @Test
    fun cluster_respectsTimeWindowConstraint() {
        val now = System.currentTimeMillis()
        val a1 = createArticle(
            id = "1",
            title = "Apple ra mắt iPhone 16 Pro Max",
            feed = feedVnExpress,
            date = Date(now),
        )
        val a2 = createArticle(
            id = "2",
            title = "Apple ra mắt iPhone 16 Pro Max",
            feed = feedTuoiTre,
            date = Date(now - TimeUnit.DAYS.toMillis(4)), // 4 days ago (> 48h limit)
        )

        val result = engine.cluster(listOf(a1, a2), timeWindowHours = 48L)
        assertTrue("Articles published 4 days apart should NOT cluster", result.clusters.isEmpty())
        assertTrue("nonLeadIds should be empty", result.nonLeadIds.isEmpty())
    }

    @Test
    fun cluster_leavesSingletonsUnclustered() {
        val articles = listOf(
            createArticle("1", "Tin tức văn hóa nghệ thuật tuần này", feed = feedVnExpress),
            createArticle("2", "Công nghệ bán dẫn thế hệ mới của TSMC", feed = feedTechCrunch),
            createArticle("3", "Bí quyết tập thể dục buổi sáng hiệu quả", feed = feedTuoiTre),
        )

        val result = engine.cluster(articles)
        assertTrue("No clusters should be formed for disparate topics", result.clusters.isEmpty())
        assertTrue(result.leadClusterMap.isEmpty())
        assertTrue(result.nonLeadIds.isEmpty())
    }

    @Test
    fun extractKeywords_identifiesSalientEventKeywords() {
        val articles = listOf(
            createArticle("1", "OpenAI ra mắt mô hình GPT-5 với khả năng tư duy đột phá", feed = feedTechCrunch),
            createArticle("2", "Mô hình GPT-5 của OpenAI chính thức trình làng công nghệ", feed = feedBBC),
        )

        val keywords = engine.extractKeywords(articles)
        assertTrue("Keywords should contain OpenAI or Gpt-5", keywords.any { it.contains("openai", ignoreCase = true) || it.contains("gpt", ignoreCase = true) })
    }

    @Test
    fun featureCache_reusesCachedFeaturesOnSubsequentCalls() {
        val a1 = createArticle("art_1", "Thị trường chứng khoán phục hồi mạnh mẽ sau phiên giảm", feed = feedVnExpress)
        assertEquals(0, engine.cacheSize())

        val f1 = engine.getOrExtractFeatures(a1)
        assertEquals(1, engine.cacheSize())

        val f2 = engine.getOrExtractFeatures(a1)
        assertEquals("Should retrieve exactly same instance from cache", f1, f2)
        assertEquals(1, engine.cacheSize())
    }

    @Test
    fun featureCache_invalidatesWhenArticleContentChanges() {
        val a1 = createArticle("art_1", "Tiêu đề ban đầu của bài báo", feed = feedVnExpress)
        val f1 = engine.getOrExtractFeatures(a1)

        val a1Updated = createArticle("art_1", "Tiêu đề đã được cập nhật khẩn cấp của bài báo", feed = feedVnExpress)
        val f2 = engine.getOrExtractFeatures(a1Updated)

        assertNotEquals("Fingerprint must change when title changes", f1.fingerprint, f2.fingerprint)
        assertFalse("Tokens of updated article must differ", f1.tokens == f2.tokens)
    }

    @Test
    fun invertedIndexBlocking_clustersArticlesSharingEntitiesOrBigrams() {
        val now = System.currentTimeMillis()
        val a1 = createArticle(
            id = "x1",
            title = "OpenAI chính thức công bố mô hình GPT-5 với năng lực suy luận vượt trội",
            description = "Sự kiện ra mắt toàn cầu của OpenAI đã trình làng mô hình trí tuệ nhân tạo GPT-5.",
            feed = feedTechCrunch,
            date = Date(now),
        )
        val a2 = createArticle(
            id = "x2",
            title = "OpenAI công bố GPT-5 với năng lực suy luận tư duy thế hệ mới",
            description = "Trí tuệ nhân tạo GPT-5 của OpenAI chính thức ra mắt toàn cầu với khả năng suy luận vượt trội.",
            feed = feedBBC,
            date = Date(now - TimeUnit.HOURS.toMillis(1)),
        )

        val result = engine.cluster(listOf(a1, a2))
        assertEquals(1, result.clusters.size)
        assertTrue(result.clusters.first().keywords.any { it.contains("openai", ignoreCase = true) || it.contains("gpt", ignoreCase = true) })
    }
}
