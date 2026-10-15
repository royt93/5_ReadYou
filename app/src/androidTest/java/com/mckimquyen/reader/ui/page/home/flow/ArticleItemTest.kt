package com.mckimquyen.reader.ui.page.home.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ArticleItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun articleItem_displaysFallbackSummaryText() {
        val summary = "The judge who helped convict Jair Bolsonaro is caught up in a crisis."

        composeRule.setContent { ArticleItem(articleWithSummary(summary)) }

        composeRule.onNodeWithText(summary).assertIsDisplayed()
    }

    @Test
    fun articleItem_hidesBlankSummary() {
        val blank = "  "

        composeRule.setContent { ArticleItem(articleWithSummary(blank)) }

        composeRule.onNodeWithText("Guardian report").assertIsDisplayed()
        composeRule.onNodeWithText(blank).assertDoesNotExist()
    }

    private fun articleWithSummary(summary: String) = ArticleWithFeed(
        article = Article(
            id = "guardian-article",
            date = Date(1_000L),
            title = "Guardian report",
            rawDescription = summary,
            shortDescription = summary,
            link = "https://example.test/article",
            feedId = "guardian-feed",
            accountId = 1,
        ),
        feed = Feed(
            id = "guardian-feed",
            name = "The Guardian",
            url = "https://www.theguardian.com/world/rss",
            groupId = "default-group",
            accountId = 1,
        ),
    )
}
