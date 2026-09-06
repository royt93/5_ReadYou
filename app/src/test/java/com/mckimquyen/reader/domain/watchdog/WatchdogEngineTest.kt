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
}
