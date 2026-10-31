package com.mckimquyen.reader.ui.page.home.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.runtime.CompositionLocalProvider
import com.mckimquyen.reader.domain.headline.HeadlineTone
import com.mckimquyen.reader.domain.headline.HeadlineSignal
import com.mckimquyen.reader.domain.headline.HeadlineLanguage
import com.mckimquyen.reader.ui.page.headline.LocalHeadlineSignalsEnabled
import com.mckimquyen.reader.ui.page.headline.CLICKBAIT_BADGE_TAG
import com.mckimquyen.reader.ui.page.headline.TONE_BADGE_TAG
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

    @Test
    fun clickbaitHeadline_displaysBothSignals() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                ArticleItem(articleWithSummary("Short summary", title = "SHOCKING: You won't believe what happened next!!!"))
            }
        }

        composeRule.onNodeWithTag(CLICKBAIT_BADGE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(TONE_BADGE_TAG).assertIsDisplayed()
    }

    @Test
    fun ordinaryHeadline_hasToneButNoClickbaitBadge() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                ArticleItem(articleWithSummary("Short summary", title = "Chancellor to cut tariffs on some staple goods"))
            }
        }

        composeRule.onAllNodesWithTag(CLICKBAIT_BADGE_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(TONE_BADGE_TAG).assertIsDisplayed()
    }

    @Test
    fun disablingHeadlineSignals_hidesBothBadges() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides false) {
                ArticleItem(articleWithSummary("Short summary", title = "SHOCKING: You won't believe what happened next!!!"))
            }
        }

        composeRule.onAllNodesWithTag(CLICKBAIT_BADGE_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TONE_BADGE_TAG).assertCountEquals(0)
    }

    private fun articleWithSummary(summary: String, title: String = "Guardian report") = ArticleWithFeed(
        article = Article(
            id = "guardian-article",
            date = Date(1_000L),
            title = title,
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
