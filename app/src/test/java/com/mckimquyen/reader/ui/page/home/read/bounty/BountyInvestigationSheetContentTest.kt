package com.mckimquyen.reader.ui.page.home.read.bounty

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.bounty.BountyInvestigationResult
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BountyInvestigationSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun item(id: String, title: String, feedName: String = "BBC", date: Long = 1_700_000_000_000L) =
        ArticleListItemWithFeed(
            article = ArticleListItem(
                id = id, date = Date(date), title = title, author = null, shortDescription = "", img = null,
                link = "https://e.com/$id", feedId = "f-$feedName", accountId = 1, isUnread = true, isStarred = false,
                isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
                audioMime = null, audioDurationSec = null,
            ),
            feed = Feed(id = "f-$feedName", name = feedName, url = "https://e.com", groupId = "g", accountId = 1),
        )

    private fun results(vararg articles: ArticleListItemWithFeed) = BountyInvestigationState(
        phase = BountyInvestigationPhase.RESULTS,
        query = "DeepSeek",
        result = BountyInvestigationResult(
            query = "DeepSeek",
            articles = articles.toList(),
            sourceCount = articles.map { it.article.feedId }.distinct().size,
            earliestDate = articles.minOfOrNull { it.article.date },
            latestDate = articles.maxOfOrNull { it.article.date },
        ),
    )

    private fun show(
        state: BountyInvestigationState,
        onOpenArticle: (String) -> Unit = {},
        onRequestAi: () -> Unit = {},
        onClose: () -> Unit = {},
    ) = compose.setContent {
        BountyInvestigationSheetContent(
            state = state,
            onOpenArticle = onOpenArticle,
            onRequestAiContext = onRequestAi,
            onClose = onClose,
        )
    }

    @Test
    fun scanning_showsRadarAndQueryStatus() {
        show(BountyInvestigationState(phase = BountyInvestigationPhase.SCANNING, query = "DeepSeek"))

        compose.onNodeWithTag(BOUNTY_RADAR_TAG).assertIsDisplayed()
        compose.onNodeWithText("Scanning your news archive for: DeepSeek…").assertIsDisplayed()
        compose.onAllNodesWithTag(BOUNTY_RESULT_LIST_TAG).assertCountEquals(0)
    }

    @Test
    fun results_showStatsAndRelatedArticles() {
        show(results(item("1", "DeepSeek-R1 launches", "BBC"), item("2", "DeepSeek explained", "Wired")))

        compose.onNodeWithText("2 related articles • 2 sources").assertIsDisplayed()
        compose.onNodeWithTag(BOUNTY_RESULT_LIST_TAG).assertIsDisplayed()
        compose.onNodeWithText("BBC", substring = true).assertIsDisplayed()
        compose.onAllNodesWithTag(BOUNTY_RADAR_TAG).assertCountEquals(0)
    }

    @Test
    fun results_emptyShowsEmptyStateInsteadOfList() {
        show(results())

        compose.onNodeWithText("No other articles in your archive mention this entity.").assertIsDisplayed()
        compose.onAllNodesWithTag(BOUNTY_RESULT_LIST_TAG).assertCountEquals(0)
    }

    @Test
    fun tappingArticle_reportsItsIdOnce() {
        val opened = mutableListOf<String>()
        show(results(item("42", "DeepSeek-R1 launches")), onOpenArticle = { opened += it })

        compose.onNodeWithTag(BOUNTY_ARTICLE_TAG_PREFIX + "42").performClick()

        assertEquals(listOf("42"), opened)
    }

    @Test
    fun aiAction_hiddenWithoutCustomKey_shownWithKey() {
        val base = results(item("1", "DeepSeek-R1 launches"))
        var calls = 0
        show(base.copy(canUseAiContext = true), onRequestAi = { calls++ })

        compose.onNodeWithTag(BOUNTY_AI_BUTTON_TAG).assertIsDisplayed().performClick()
        assertEquals(1, calls)
    }

    @Test
    fun aiAction_absentWhenNoCustomKey() {
        show(results(item("1", "DeepSeek-R1 launches")).copy(canUseAiContext = false))

        compose.onAllNodesWithTag(BOUNTY_AI_BUTTON_TAG).assertCountEquals(0)
    }

    @Test
    fun aiReady_showsSummaryAndFailureShowsMessage() {
        show(results(item("1", "DeepSeek-R1 launches")).copy(canUseAiContext = true, ai = BountyAiState.Ready("- bullet one")))
        compose.onNodeWithText("- bullet one").assertIsDisplayed()
    }

    @Test
    fun aiFailure_showsNoKeyHint() {
        show(
            results(item("1", "DeepSeek-R1 launches"))
                .copy(canUseAiContext = true, ai = BountyAiState.Failed(BountyAiFailure.NO_KEY)),
        )
        compose.onNodeWithText("Add your own Gemini API key in AI settings to use this.").assertIsDisplayed()
    }

    @Test
    fun errorPhase_showsErrorMessage() {
        show(BountyInvestigationState(phase = BountyInvestigationPhase.ERROR, query = "DeepSeek"))

        compose.onNodeWithText("Could not search your archive. Try again.").assertIsDisplayed()
    }

    @Test
    fun closeButton_invokesCallback() {
        var closed = 0
        show(results(), onClose = { closed++ })

        compose.onNodeWithTag(BOUNTY_CLOSE_TAG).performClick()

        assertEquals(1, closed)
        compose.onAllNodesWithText("zzz").assertCountEquals(0)
    }
}
