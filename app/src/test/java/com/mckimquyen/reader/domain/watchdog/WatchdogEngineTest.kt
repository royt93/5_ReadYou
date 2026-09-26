package com.mckimquyen.reader.domain.watchdog

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.watchdog.WatchdogKeyword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class WatchdogEngineTest {

    private lateinit var engine: WatchdogEngine

    @Before
    fun setUp() {
        engine = WatchdogEngine()
    }

    private fun createArticle(
        title: String,
        description: String = "",
        content: String? = null,
    ): Article {
        return Article(
            id = "art_1",
            title = title,
            rawDescription = description,
            shortDescription = description,
            fullContent = content,
            link = "https://example.com/test",
            feedId = "feed_1",
            accountId = 1,
            date = Date(),
        )
    }

    @Test
    fun matchesText_tickerWithDollar_matchesBothDollarAndBareWord() {
        assertTrue(WatchdogEngine.matchesText("\$VIC", "Cổ phiếu \$VIC tăng trần phiên hôm nay"))
        assertTrue(WatchdogEngine.matchesText("\$VIC", "Khối ngoại mua ròng VIC đột biến"))
        assertFalse(WatchdogEngine.matchesText("\$VIC", "Chiến thắng viễn xứ victor"))
    }

    @Test
    fun matchesText_shortWord_matchesWithWordBoundaries() {
        assertTrue(WatchdogEngine.matchesText("vàng", "Giá vàng trong nước vượt 85 triệu"))
        assertTrue(WatchdogEngine.matchesText("vàng", "Thị trường vàng thế giới lập đỉnh"))
        assertFalse(WatchdogEngine.matchesText("vàng", "Không gian hoangvàng mông lung"))
    }

    @Test
    fun matchesText_longerPhrase_matchesSubstring() {
        assertTrue(WatchdogEngine.matchesText("lãi suất", "Ngân hàng hạ lãi suất điều hành"))
        assertTrue(WatchdogEngine.matchesText("lãi suất", "Dự báo LÃI SUẤT liên ngân hàng"))
        assertFalse(WatchdogEngine.matchesText("lãi suất", "Thị trường bất động sản phục hồi"))
    }

    @Test
    fun match_matchesInTitle() {
        val article = createArticle(title = "Cổ phiếu \$FPT lập đỉnh lịch sử nhờ làn sóng AI")
        val keywords = listOf(
            WatchdogKeyword(id = "1", keyword = "\$FPT"),
            WatchdogKeyword(id = "2", keyword = "Bitcoin"),
        )
        val matched = engine.match(article, keywords)
        assertNotNull(matched)
        assertEquals("1", matched?.id)
        assertEquals("\$FPT", matched?.keyword)
    }

    @Test
    fun match_matchesInDescription() {
        val article = createArticle(
            title = "Thị trường tiền số biến động mạnh",
            description = "Giá Bitcoin hôm nay vượt ngưỡng 100,000 USD sau tin tức từ FED."
        )
        val keywords = listOf(
            WatchdogKeyword(id = "1", keyword = "\$VIC"),
            WatchdogKeyword(id = "2", keyword = "Bitcoin"),
        )
        val matched = engine.match(article, keywords)
        assertNotNull(matched)
        assertEquals("2", matched?.id)
        assertEquals("Bitcoin", matched?.keyword)
    }

    @Test
    fun match_matchesInContent() {
        val article = createArticle(
            title = "Bản tin thời tiết miền Trung",
            description = "Dự báo tình hình thời tiết tuần tới",
            content = "Miền Trung đối mặt nguy cơ bão lũ dồn dập vào cuối tuần"
        )
        val keywords = listOf(
            WatchdogKeyword(id = "1", keyword = "bão lũ"),
        )
        val matched = engine.match(article, keywords)
        assertNotNull(matched)
        assertEquals("bão lũ", matched?.keyword)
    }

    @Test
    fun match_ignoresDisabledKeywords() {
        val article = createArticle(title = "Giá vàng thế giới giảm nhẹ")
        val keywords = listOf(
            WatchdogKeyword(id = "1", keyword = "giá vàng", isEnabled = false),
        )
        val matched = engine.match(article, keywords)
        assertNull(matched)
    }

    @Test
    fun match_returnsNullWhenNoKeywordMatches() {
        val article = createArticle(
            title = "Tuyển dụng kỹ sư Android tại TP.HCM",
            description = "Yêu cầu kinh nghiệm Kotlin, Jetpack Compose và Clean Architecture"
        )
        val keywords = listOf(
            WatchdogKeyword(id = "1", keyword = "\$VIC"),
            WatchdogKeyword(id = "2", keyword = "Bitcoin"),
            WatchdogKeyword(id = "3", keyword = "Lãi suất"),
        )
        val matched = engine.match(article, keywords)
        assertNull(matched)
    }

    @Test
    fun match_handlesEmptyKeywordsList() {
        val article = createArticle(title = "Bất kỳ tiêu đề nào")
        assertNull(engine.match(article, emptyList()))
    }

    // ---- REEL-05: compiled-matcher cache reused across calls, not recompiled per call ----

    @Test
    fun matchesText_repeatedCallsWithSameKeyword_reuseSingleCachedEntry() {
        val cache = matcherCacheField()
        val uniqueKeyword = "cache_test_kw_${System.nanoTime()}"
        val sizeBefore = cache.size

        repeat(100) { i ->
            WatchdogEngine.matchesText(uniqueKeyword, "văn bản thứ $i không khớp gì cả")
        }

        // A brand-new keyword compiles its matcher exactly once, regardless of call count.
        assertEquals(sizeBefore + 1, cache.size)
    }

    @Test
    fun matchesText_differentKeywords_eachGetsExactlyOneCacheEntry() {
        val cache = matcherCacheField()
        val kw1 = "cache_test_a_${System.nanoTime()}"
        val kw2 = "cache_test_b_${System.nanoTime()}"
        val sizeBefore = cache.size

        repeat(10) {
            WatchdogEngine.matchesText(kw1, "một đoạn văn bản")
            WatchdogEngine.matchesText(kw2, "một đoạn văn bản khác")
        }

        assertEquals(sizeBefore + 2, cache.size)
    }

    @Suppress("UNCHECKED_CAST")
    private fun matcherCacheField(): java.util.concurrent.ConcurrentHashMap<String, Any> {
        // Kotlin hoists a private companion-object field with no external reads to a static field
        // on the outer class rather than an instance field on Companion.
        val field = WatchdogEngine::class.java.getDeclaredField("matcherCache")
        field.isAccessible = true
        return field.get(null) as java.util.concurrent.ConcurrentHashMap<String, Any>
    }

    // ---- REEL-05: fullContent scanning is bounded, not the entire article body ----

    @Test
    fun match_keywordWithinScanLimit_isFound() {
        val padding = "x".repeat(500)
        val article = createArticle(
            title = "Bản tin",
            content = "$padding Bitcoin đạt đỉnh mới $padding",
        )
        val matched = engine.match(article, listOf(WatchdogKeyword(id = "1", keyword = "Bitcoin")))
        assertNotNull(matched)
    }

    @Test
    fun match_keywordBeyondScanLimit_isNotFound() {
        // Keyword appears only after the first 2000 characters of fullContent -> must not match,
        // proving the scan is bounded rather than reading the entire article body.
        val padding = "x".repeat(2500)
        val article = createArticle(
            title = "Bản tin",
            description = "Mô tả không liên quan",
            content = "$padding Bitcoin đạt đỉnh mới",
        )
        val matched = engine.match(article, listOf(WatchdogKeyword(id = "1", keyword = "Bitcoin")))
        assertNull(matched)
    }
}
