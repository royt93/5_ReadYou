package com.mckimquyen.reader.ui.component.onthisday

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnThisDayCardWidgetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun card_showsTitleDescriptionAndYearBadges() {
        composeTestRule.setContent {
            OnThisDayCard(
                articles = listOf(item("a1", 2025), item("a2", 2024)),
                currentYear = CURRENT_YEAR,
                onArticleClick = {},
                onSeeAllClick = {},
            )
        }

        composeTestRule.onNodeWithText("On This Day").assertIsDisplayed()
        composeTestRule.onNodeWithText("Title a1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Feed · 1 year ago").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Feed · 2 years ago").assertIsDisplayed()
    }

    @Test
    fun card_tapRowReportsArticleId() {
        val clicked = mutableListOf<String>()
        composeTestRule.setContent {
            OnThisDayCard(
                articles = listOf(item("a1", 2025), item("a2", 2024)),
                currentYear = CURRENT_YEAR,
                onArticleClick = { clicked += it },
                onSeeAllClick = {},
            )
        }

        composeTestRule.onNodeWithTag(ON_THIS_DAY_ROW_TAG_PREFIX + "a2").performClick()

        assertEquals(listOf("a2"), clicked)
    }

    @Test
    fun card_tapHeaderTriggersSeeAll() {
        var seeAll = 0
        composeTestRule.setContent {
            OnThisDayCard(
                articles = listOf(item("a1", 2025)),
                currentYear = CURRENT_YEAR,
                onArticleClick = {},
                onSeeAllClick = { seeAll++ },
            )
        }

        composeTestRule.onNodeWithTag(ON_THIS_DAY_SEE_ALL_TAG).performClick()

        assertEquals(1, seeAll)
    }

    @Test
    fun card_neverShowsMoreThanFiveRows() {
        val many = (2015..2025).map { item("y$it", it) }
        composeTestRule.setContent {
            OnThisDayCard(
                articles = many,
                currentYear = CURRENT_YEAR,
                onArticleClick = {},
                onSeeAllClick = {},
            )
        }

        composeTestRule.onAllNodes(hasTestTagPrefix(ON_THIS_DAY_ROW_TAG_PREFIX))
            .assertCountEquals(ON_THIS_DAY_MAX_ROWS)
        composeTestRule.onNodeWithTag(ON_THIS_DAY_ROW_TAG_PREFIX + "y2015").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ON_THIS_DAY_ROW_TAG_PREFIX + "y2020").assertDoesNotExist()
    }

    @Test
    fun yearsAgo_usesLocalYearAndNeverBelowOne() {
        val zone = ZoneId.of("UTC")
        val twoYears = Date.from(LocalDateTime.of(2024, 10, 18, 12, 0).atZone(zone).toInstant())
        val sameYear = Date.from(LocalDateTime.of(2026, 10, 18, 12, 0).atZone(zone).toInstant())

        assertEquals(2, yearsAgo(twoYears, CURRENT_YEAR, zone))
        assertEquals(1, yearsAgo(sameYear, CURRENT_YEAR, zone))
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun item(id: String, year: Int) = ArticleWithFeed(
        article = Article(
            id = id,
            date = Date.from(LocalDateTime.of(year, 10, 18, 12, 0).atZone(ZoneId.systemDefault()).toInstant()),
            title = "Title $id",
            rawDescription = "",
            shortDescription = "",
            link = "https://example.com/$id",
            feedId = "feed-1",
            accountId = 1,
            isUnread = false,
        ),
        feed = Feed(id = "feed-1", name = "Test Feed", url = "https://example.com/rss", groupId = "g", accountId = 1),
    )

    private companion object {
        const val CURRENT_YEAR = 2026
    }
}
