package com.mckimquyen.reader.ui.page.home.read.bounty

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.bounty.BountyInvestigationResult
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class BountyInvestigationSheetDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun item(id: String, title: String) = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id, date = Date(1_700_000_000_000L), title = title, author = null, shortDescription = "", img = null,
            link = "https://e.com/$id", feedId = "f", accountId = 1, isUnread = true, isStarred = false,
            isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
            audioMime = null, audioDurationSec = null,
        ),
        feed = Feed(id = "f", name = "BBC", url = "https://e.com", groupId = "g", accountId = 1),
    )

    private fun results(canUseAi: Boolean) = BountyInvestigationState(
        phase = BountyInvestigationPhase.RESULTS,
        query = "DeepSeek",
        result = BountyInvestigationResult(
            query = "DeepSeek",
            articles = listOf(item("42", "DeepSeek-R1 launches")),
            sourceCount = 1,
            earliestDate = Date(1_700_000_000_000L),
            latestDate = Date(1_700_000_000_000L),
        ),
        canUseAiContext = canUseAi,
    )

    @Test
    fun scanning_rendersRadarOnRealRenderer() {
        compose.setContent {
            BountyInvestigationSheetContent(
                state = BountyInvestigationState(phase = BountyInvestigationPhase.SCANNING, query = "DeepSeek"),
                onOpenArticle = {}, onRequestAiContext = {}, onClose = {},
            )
        }

        compose.onNodeWithTag(BOUNTY_RADAR_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(BOUNTY_RESULT_LIST_TAG).assertCountEquals(0)
    }

    @Test
    fun results_tapOpensArticleAndAiButtonFollowsKeyAvailability() {
        val opened = mutableListOf<String>()
        compose.setContent {
            BountyInvestigationSheetContent(
                state = results(canUseAi = false),
                onOpenArticle = { opened += it }, onRequestAiContext = {}, onClose = {},
            )
        }

        compose.onNodeWithTag(BOUNTY_RESULT_LIST_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(BOUNTY_AI_BUTTON_TAG).assertCountEquals(0)
        compose.onNodeWithTag(BOUNTY_ARTICLE_TAG_PREFIX + "42").performClick()
        assertEquals(listOf("42"), opened)
    }

    @Test
    fun results_aiButtonShownWithKeyAndInvokesCallback() {
        var calls = 0
        compose.setContent {
            BountyInvestigationSheetContent(
                state = results(canUseAi = true),
                onOpenArticle = {}, onRequestAiContext = { calls++ }, onClose = {},
            )
        }

        compose.onNodeWithTag(BOUNTY_AI_BUTTON_TAG).assertIsDisplayed().performClick()

        assertEquals(1, calls)
    }
}
