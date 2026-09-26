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

    // ---- REEL-06: excerpt extraction around the first match ----

    @Test
    fun extractExcerpt_keywordInTitle_keepsSurroundingContext() {
        val excerpt = WatchdogEngine.extractExcerpt(
            keyword = "Bitcoin",
            title = "Thị trường tiền số: Bitcoin đạt đỉnh mới trong phiên châu Á",
            desc = "",
            content = null,
        )
        assertTrue(excerpt.contains("Bitcoin"))
        assertTrue(excerpt.contains("đạt đỉnh"))
    }

    @Test
    fun extractExcerpt_keywordOnlyInContent_usesContentAndAddsEllipsisWhenClipped() {
        val padding = "x".repeat(80)
        val excerpt = WatchdogEngine.extractExcerpt(
            keyword = "Bitcoin",
            title = "Bản tin",
            desc = "Mô tả không liên quan",
            content = "$padding Bitcoin tăng mạnh $padding",
        )
        assertTrue(excerpt.contains("Bitcoin"))
        assertTrue(excerpt.startsWith("…") || excerpt.startsWith("x"))
        assertTrue(excerpt.length < 80 + 7 + 80)
    }

    @Test
    fun extractExcerpt_tickerWithDollar_alsoFindsBareTickerInText() {
        val excerpt = WatchdogEngine.extractExcerpt(
            keyword = "\$VIC",
            title = "Khối ngoại mua ròng VIC đột biến",
            desc = "",
            content = null,
        )
        assertTrue(excerpt.contains("VIC"))
    }

    @Test
    fun extractExcerpt_noMatchAnywhere_fallsBackToTitle() {
        val excerpt = WatchdogEngine.extractExcerpt(
            keyword = "Bitcoin",
            title = "Bản tin thời tiết",
            desc = "Nắng nóng",
            content = "Mưa rào",
        )
        assertEquals("Bản tin thời tiết", excerpt)
    }

    // ---- REEL-06: snooze + quiet hours (including overnight wrap) ----

    @Test
    fun isMuted_snoozeInTheFuture_isMuted() {
        val now = 1_000_000L
        val kw = WatchdogKeyword(keyword = "Bitcoin", snoozeUntil = now + 60_000)
        assertTrue(WatchdogEngine.isMuted(kw, now, minuteOfDay = 12 * 60))
    }

    @Test
    fun isMuted_snoozeAlreadyExpired_isNotMuted() {
        val now = 1_000_000L
        val kw = WatchdogKeyword(keyword = "Bitcoin", snoozeUntil = now - 1)
        assertFalse(WatchdogEngine.isMuted(kw, now, minuteOfDay = 12 * 60))
    }

    @Test
    fun isMuted_quietHoursSameDay_mutesOnlyInsideWindow() {
        // 09:00–17:00
        val kw = WatchdogKeyword(keyword = "Bitcoin", quietHoursStart = 9 * 60, quietHoursEnd = 17 * 60)
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 8 * 60 + 59))
        assertTrue(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 9 * 60))
        assertTrue(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 16 * 60 + 59))
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 17 * 60))
    }

    @Test
    fun isMuted_quietHoursOvernight_wrapsAcrossMidnight() {
        // 22:00–07:00
        val kw = WatchdogKeyword(keyword = "Bitcoin", quietHoursStart = 22 * 60, quietHoursEnd = 7 * 60)
        assertTrue(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 22 * 60))
        assertTrue(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 23 * 60 + 59))
        assertTrue(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 0))
        assertTrue(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 6 * 60 + 59))
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 7 * 60))
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 12 * 60))
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 21 * 60 + 59))
    }

    @Test
    fun isMuted_quietHoursStartEqualsEnd_treatedAsDisabled() {
        val kw = WatchdogKeyword(keyword = "Bitcoin", quietHoursStart = 22 * 60, quietHoursEnd = 22 * 60)
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 22 * 60))
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 0))
    }

    @Test
    fun isMuted_noSnoozeNoQuietHours_neverMuted() {
        val kw = WatchdogKeyword(keyword = "Bitcoin")
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 0))
        assertFalse(WatchdogEngine.isMuted(kw, nowMillis = 0, minuteOfDay = 23 * 60 + 59))
    }
}
