package com.mckimquyen.reader.ui.page.statistics

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.statistics.FeedReadCount
import com.mckimquyen.reader.domain.model.statistics.ReadingDayCount
import com.mckimquyen.reader.domain.model.statistics.ReadingStatisticsCalendar
import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingStatisticsPageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 18)
    private val calendar: ReadingStatisticsCalendar = mockk<ReadingStatisticsCalendar>().also {
        every { it.today() } returns LocalDate.of(2026, 10, 18)
        every { it.windowStartMillis() } returns 0L
    }

    private fun viewModel(dao: ArticleDao) = ReadingStatisticsViewModel(app, dao, calendar, Dispatchers.Unconfined)

    @Test
    fun page_emptyState_showsEmptyText() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryTotalRead(any()) } returns 0

        composeTestRule.setContent { ReadingStatisticsPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(STATS_PAGE_TITLE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(STATS_EMPTY_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("No reading history yet. Statistics start counting from this update.").assertIsDisplayed()
    }

    @Test
    fun page_errorState_showsMessageAndRetryRequeries() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryTotalRead(any()) } throws IOException("boom")

        composeTestRule.setContent { ReadingStatisticsPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(STATS_ERROR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Could not load statistics. Tap to retry.").assertIsDisplayed()

        composeTestRule.onNodeWithTag(STATS_RETRY_TAG).performClick()
        composeTestRule.waitForIdle()

        coVerify(atLeast = 2) { dao.queryTotalRead(any()) }
    }

    @Test
    fun page_contentState_showsNumbersHeatmapAndTopFeedsInOrder() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryTotalRead(any()) } returns 12
        coEvery { dao.queryReadingDayCounts(any(), any()) } returns listOf(
            ReadingDayCount("2026-10-16", 1),
            ReadingDayCount("2026-10-17", 2),
            ReadingDayCount("2026-10-18", 3),
        )
        coEvery { dao.queryTopFeeds(any(), any()) } returns listOf(
            FeedReadCount("f-alpha", "Alpha Daily", 7),
            FeedReadCount("f-beta", "Beta Weekly", 2),
        )

        composeTestRule.setContent { ReadingStatisticsPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(STATS_CONTENT_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total read").assertIsDisplayed()
        // 3 ngày liên tiếp (16-18/10): chuỗi hiện tại và chuỗi dài nhất đều là "3 days" => 2 thẻ.
        composeTestRule.onAllNodesWithTextCount("3 days", expected = 2)

        composeTestRule.onNodeWithTag(STATS_CONTENT_TAG).performScrollToNode(hasText("Alpha Daily"))
        composeTestRule.onNodeWithTag(STATS_TOP_FEED_TAG_PREFIX + "f-alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 articles").assertIsDisplayed()
    }

    @Test
    fun page_contentState_hidesBadgesBelowThresholdAndShowsWeekBadgeAtSevenDays() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryTotalRead(any()) } returns 3
        coEvery { dao.queryTopFeeds(any(), any()) } returns emptyList()
        coEvery { dao.queryReadingDayCounts(any(), any()) } returns listOf(
            ReadingDayCount("2026-10-18", 1),
        )

        composeTestRule.setContent { ReadingStatisticsPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(STATS_CONTENT_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTextCount("7-day streak", expected = 0)
    }

    @Test
    fun page_contentState_showsWeekBadgeWhenLongestStreakReachesSevenDays() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryTotalRead(any()) } returns 20
        coEvery { dao.queryTopFeeds(any(), any()) } returns emptyList()
        coEvery { dao.queryReadingDayCounts(any(), any()) } returns (12..18).map {
            ReadingDayCount("2026-10-$it", 1)
        }

        composeTestRule.setContent { ReadingStatisticsPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(STATS_CONTENT_TAG).performScrollToNode(hasText("7-day streak"))
        composeTestRule.onNodeWithText("7-day streak").assertIsDisplayed()
        composeTestRule.onAllNodesWithTextCount("30-day streak", expected = 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, expected: Int) {
        onAllNodes(hasText(text)).assertCountEquals(expected)
    }
}
