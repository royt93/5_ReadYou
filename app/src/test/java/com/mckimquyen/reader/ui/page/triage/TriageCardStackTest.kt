package com.mckimquyen.reader.ui.page.triage

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TriageCardStackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleArticle = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = "test_1",
            date = Date(1000L),
            title = "Breaking News Title",
            author = "Reporter",
            shortDescription = "Sample snippet of the news story.",
            img = null,
            link = "https://example.com/news",
            feedId = "feed_1",
            accountId = 1,
            isUnread = true,
            isStarred = false,
            isReadLater = false,
            updateAt = Date(1000L),
            aiSummary = null,
            readAt = null,
            audioUrl = null,
            audioMime = null,
            audioDurationSec = null,
        ),
        feed = Feed(id = "feed_1", name = "BBC News", url = "https://bbc.com/rss", groupId = "group_1", accountId = 1),
    )

    @Test
    fun triageCard_displaysFeedNameTitleAndDescription() {
        composeRule.setContent {
            TriageCard(item = sampleArticle)
        }

        composeRule.onNodeWithText("BBC News").assertExists()
        composeRule.onNodeWithText("Breaking News Title").assertExists()
        composeRule.onNodeWithText("Sample snippet of the news story.").assertExists()
    }

    @Test
    fun triageBottomBar_triggersCallbacksOnButtonClick() {
        var skipped = false
        var readNow = false
        var saved = false

        composeRule.setContent {
            TriageBottomBar(
                onSkip = { skipped = true },
                onReadNow = { readNow = true },
                onSave = { saved = true },
            )
        }

        composeRule.onNodeWithTag(TRIAGE_BTN_SKIP_TAG).performClick()
        assertTrue(skipped)

        composeRule.onNodeWithTag(TRIAGE_BTN_READ_NOW_TAG).performClick()
        assertTrue(readNow)

        composeRule.onNodeWithTag(TRIAGE_BTN_SAVE_TAG).performClick()
        assertTrue(saved)
    }

    @Test
    fun triageInboxZeroView_displaysSummaryStatsAndFinishButton() {
        var finished = false

        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                TriageInboxZeroView(
                    processedCount = 42,
                    savedCount = 10,
                    skippedCount = 32,
                    totalXpEarned = 210L,
                    onFinish = { finished = true },
                )
            }
        }

        composeRule.onNodeWithText("🎉 Inbox Zero Reached!").assertExists()
        composeRule.onNodeWithText("Processed: 42").assertExists()
        composeRule.onNodeWithText("Saved for later: 10").assertExists()
        composeRule.onNodeWithText("Marked as read: 32").assertExists()
        composeRule.onNodeWithText("+210 Brain RPG XP").assertExists()

        composeRule.onNodeWithTag(TRIAGE_BTN_FINISH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(finished)
    }
}
