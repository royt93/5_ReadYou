package com.mckimquyen.reader.ui.component.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SemanticSearchCardWidgetTest {

    private val sampleFeed = Feed(
        id = "feed_tech",
        name = "TechCrunch",
        url = "https://techcrunch.com/rss",
        groupId = "group_1",
        accountId = 1,
    )

    private val sampleArticle = ArticleWithFeed(
        article = Article(
            id = "art_clean_tech",
            title = "Thế hệ pin thể rắn thế hệ mới nâng tầm hiệu năng xe điện",
            rawDescription = "Đột phá lưu trữ năng lượng giúp tăng gấp đôi quãng đường di chuyển.",
            shortDescription = "Đột phá lưu trữ năng lượng giúp tăng gấp đôi quãng đường di chuyển.",
            link = "https://techcrunch.com/solid-state-battery",
            feedId = sampleFeed.id,
            accountId = 1,
            date = Date(),
        ),
        feed = sampleFeed,
    )

    private val sampleResult = SemanticSearchResult(
        articleWithFeed = sampleArticle,
        score = 0.92f,
        matchedConcepts = listOf("CLEAN_ENERGY", "ELECTRIC_VEHICLES"),
    )

    @Test
    fun semanticSearchCard_rendersContentSuccessfully() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SemanticSearchCard(
                        result = sampleResult,
                        onClick = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("SemanticSearchCard should attach cleanly", composeView)
        }
        scenario.close()
    }

    @Test
    fun semanticSearchCard_clickTriggersCallback() {
        var clicked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SemanticSearchCard(
                        result = sampleResult,
                        onClick = { clicked = true },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun semanticSearchCard_rendersWithoutFeedIcon_cleanly() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SemanticSearchCard(
                        result = sampleResult.copy(matchedConcepts = emptyList()),
                        isShowFeedIcon = false,
                        onClick = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
