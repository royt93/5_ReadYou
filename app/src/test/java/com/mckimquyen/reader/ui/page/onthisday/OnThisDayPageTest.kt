package com.mckimquyen.reader.ui.page.onthisday

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.onthisday.OnThisDayCalendar
import com.mckimquyen.reader.domain.model.onthisday.OnThisDayTarget
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnThisDayPageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val calendar: OnThisDayCalendar = mockk<OnThisDayCalendar>().also {
        every { it.currentTarget() } returns OnThisDayTarget("10-18", "2026")
    }

    private fun viewModel(dao: ArticleDao) = OnThisDayViewModel(app, dao, calendar, Dispatchers.Unconfined)

    @Test
    fun page_emptyState_showsEmptyText() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryOnThisDay(any(), any(), any(), any()) } returns emptyList()

        composeTestRule.setContent { OnThisDayPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(ON_THIS_DAY_PAGE_TITLE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ON_THIS_DAY_EMPTY_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("No read articles found from this day in earlier years").assertIsDisplayed()
    }

    @Test
    fun page_errorState_showsMessageAndRetryRequeries() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryOnThisDay(any(), any(), any(), any()) } throws IOException("boom")

        composeTestRule.setContent { OnThisDayPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(ON_THIS_DAY_ERROR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Could not load articles. Tap to retry.").assertIsDisplayed()

        composeTestRule.onNodeWithTag(ON_THIS_DAY_RETRY_TAG).performClick()
        composeTestRule.waitForIdle()

        coVerify(atLeast = 2) { dao.queryOnThisDay(any(), any(), any(), any()) }
    }

    @Test
    fun page_contentState_listsTitleFeedAndYearsAgo() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryOnThisDay(any(), any(), any(), any()) } returns listOf(
            item("a1", 2025),
            item("a2", 2023),
        )

        composeTestRule.setContent { OnThisDayPage(NavHostController(app), viewModel(dao)) }

        composeTestRule.onNodeWithTag(ON_THIS_DAY_LIST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Title a1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Feed · 1 year ago").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Feed · 3 years ago").assertIsDisplayed()
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
}
