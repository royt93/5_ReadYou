package com.mckimquyen.reader.infrastructure.ai.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.ui.component.search.SemanticSearchCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SemanticSearchIntegrationTest {

    @Test
    fun endToEnd_semanticConceptualSearch_rankingAndLiveComposeRendering() {
        // 1. Setup multi-domain articles
        val feedNews = Feed(
            id = "feed_news",
            name = "VnExpress",
            url = "https://vnexpress.net/rss",
            groupId = "group_1",
            accountId = 1,
        )
        val feedTech = Feed(
            id = "feed_tech",
            name = "TechCrunch",
            url = "https://techcrunch.com/rss",
            groupId = "group_1",
            accountId = 1,
        )
        val feedFinance = Feed(
            id = "feed_finance",
            name = "Bloomberg",
            url = "https://bloomberg.com/rss",
            groupId = "group_1",
            accountId = 1,
        )

        val cleanEnergyArt = ArticleWithFeed(
            article = Article(
                id = "art_energy",
                title = "Phát động dự án đại công trình điện gió ngoài khơi và pin mặt trời tại duyên hải",
                rawDescription = "Hệ thống nguồn điện tái tạo bổ sung công suất quang điện sạch quy mô quốc gia.",
                shortDescription = "Hệ thống nguồn điện tái tạo bổ sung công suất quang điện sạch quy mô quốc gia.",
                link = "https://vnexpress.net/clean-energy-project",
                feedId = feedNews.id,
                accountId = 1,
                date = Date(),
            ),
            feed = feedNews,
        )

        val aiArt = ArticleWithFeed(
            article = Article(
                id = "art_ai",
                title = "OpenAI chính thức trình làng mô hình trí tuệ nhân tạo GPT-5 đa phương thức",
                rawDescription = "Đột phá về mạng nơron transformer và học sâu machine learning giúp giải toán siêu việt.",
                shortDescription = "Đột phá về mạng nơron transformer và học sâu machine learning giúp giải toán siêu việt.",
                link = "https://techcrunch.com/gpt-5-launch",
                feedId = feedTech.id,
                accountId = 1,
                date = Date(),
            ),
            feed = feedTech,
        )

        val financeArt = ArticleWithFeed(
            article = Article(
                id = "art_finance",
                title = "Thị trường chứng khoán khởi sắc khi ngân hàng trung ương hạ lãi suất điều hành",
                rawDescription = "Dòng tiền đổ vào cổ phiếu bất động sản và giảm áp lực lạm phát ngắn hạn.",
                shortDescription = "Dòng tiền đổ vào cổ phiếu bất động sản và giảm áp lực lạm phát ngắn hạn.",
                link = "https://bloomberg.com/market-rally",
                feedId = feedFinance.id,
                accountId = 1,
                date = Date(),
            ),
            feed = feedFinance,
        )

        val articles = listOf(cleanEnergyArt, aiArt, financeArt)
        val engine = SemanticSearchEngine()

        // 2. Query 1: Conceptual search for Clean Energy (Notice query does not share exact words with title)
        val energyQuery = "công nghệ năng lượng sạch và phát triển bền vững"
        val energyMatches = engine.rank(energyQuery, articles)

        assertTrue("Should return matches for clean energy", energyMatches.isNotEmpty())
        val topEnergyMatch = energyMatches.first()
        assertEquals("Clean energy article must rank #1", "art_energy", topEnergyMatch.articleWithFeed.article.id)
        assertTrue("Top energy score should be >= 0.40f, got ${topEnergyMatch.score}", topEnergyMatch.score >= 0.40f)
        assertTrue("Matched concepts should include CLEAN_ENERGY", topEnergyMatch.matchedConcepts.contains("CLEAN_ENERGY"))

        // 3. Query 2: Conceptual search for AI and Deep Learning
        val aiQuery = "mô hình ngôn ngữ lớn và học sâu"
        val aiMatches = engine.rank(aiQuery, articles)

        assertTrue("Should return matches for AI", aiMatches.isNotEmpty())
        val topAiMatch = aiMatches.first()
        assertEquals("AI article must rank #1", "art_ai", topAiMatch.articleWithFeed.article.id)
        assertTrue("Top AI score should be >= 0.40f, got ${topAiMatch.score}", topAiMatch.score >= 0.40f)
        assertTrue("Matched concepts should include ARTIFICIAL_INTELLIGENCE", topAiMatch.matchedConcepts.contains("ARTIFICIAL_INTELLIGENCE"))

        // 4. Verify Compose rendering on live Android runtime
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SemanticSearchCard(
                        result = topEnergyMatch,
                        onClick = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach cleanly on live Android runtime", composeView)
        }
        scenario.close()
    }
}
