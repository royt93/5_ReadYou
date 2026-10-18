package com.mckimquyen.reader.ui.page.onthisday

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.onthisday.OnThisDayCalendar
import com.mckimquyen.reader.domain.model.onthisday.OnThisDayTarget
import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OnThisDayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val articleDao: ArticleDao = mockk()
    private val calendar: OnThisDayCalendar = mockk()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        every { calendar.currentTarget() } returns OnThisDayTarget("10-18", "2026")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_emitsLoadingThenContentWhenArticlesExist() = runTest(testDispatcher) {
        val sample = listOf(sampleArticle("art-1"))
        coEvery { articleDao.queryOnThisDay(any(), "10-18", "2026", limit = 5) } returns sample

        val vm = OnThisDayViewModel(app, articleDao, calendar, testDispatcher)
        assertEquals(OnThisDayUiState.Loading, vm.state.value)

        advanceUntilIdle()
        val content = vm.state.value as OnThisDayUiState.Content
        assertEquals(sample, content.articles)
        assertEquals("10-18", content.monthDay)
        assertEquals(2026, content.currentYear)
    }

    @Test
    fun init_emitsEmptyWhenDaoReturnsEmpty() = runTest(testDispatcher) {
        coEvery { articleDao.queryOnThisDay(any(), any(), any(), any()) } returns emptyList()

        val vm = OnThisDayViewModel(app, articleDao, calendar, testDispatcher)
        advanceUntilIdle()

        assertEquals(OnThisDayUiState.Empty, vm.state.value)
    }

    @Test
    fun init_emitsErrorWhenDaoThrows() = runTest(testDispatcher) {
        coEvery { articleDao.queryOnThisDay(any(), any(), any(), any()) } throws IOException("disk read error")

        val vm = OnThisDayViewModel(app, articleDao, calendar, testDispatcher)
        advanceUntilIdle()

        val error = vm.state.value as OnThisDayUiState.Error
        assertEquals(R.string.on_this_day_error, error.messageRes)
    }

    @Test
    fun refresh_queriesWithUpdatedCalendarDate() = runTest(testDispatcher) {
        coEvery { articleDao.queryOnThisDay(any(), "10-18", "2026", any()) } returns emptyList()
        val sample = listOf(sampleArticle("art-2"))
        coEvery { articleDao.queryOnThisDay(any(), "10-19", "2026", any()) } returns sample

        val vm = OnThisDayViewModel(app, articleDao, calendar, testDispatcher)
        advanceUntilIdle()
        assertEquals(OnThisDayUiState.Empty, vm.state.value)

        every { calendar.currentTarget() } returns OnThisDayTarget("10-19", "2026")
        vm.refresh()
        advanceUntilIdle()

        val content = vm.state.value as OnThisDayUiState.Content
        assertEquals("10-19", content.monthDay)
        assertEquals(sample, content.articles)
    }

    @Test
    fun refresh_usesContextCurrentAccountId() = runTest(testDispatcher) {
        val queriedAccounts = mutableListOf<Int>()
        coEvery {
            articleDao.queryOnThisDay(capture(queriedAccounts), any(), any(), any())
        } returns emptyList()

        val vm = OnThisDayViewModel(app, articleDao, calendar, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf(1), queriedAccounts)
    }

    private fun sampleArticle(id: String) = ArticleWithFeed(
        article = Article(
            id = id,
            date = Date(),
            title = "Title $id",
            rawDescription = "",
            shortDescription = "",
            link = "https://example.com/$id",
            feedId = "feed-1",
            accountId = 1,
            isUnread = false,
        ),
        feed = Feed(
            id = "feed-1",
            name = "Test Feed",
            url = "https://example.com/rss",
            groupId = "group-1",
            accountId = 1,
        ),
    )
}
