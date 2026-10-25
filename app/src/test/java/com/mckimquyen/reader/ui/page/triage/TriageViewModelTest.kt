package com.mckimquyen.reader.ui.page.triage

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class TriageViewModelTest {

    private val articleDao: ArticleDao = mockk(relaxed = true)
    private val widgetRefreshNotifier: WidgetRefreshNotifier = mockk(relaxed = true)
    private val brainRpgRepository: BrainRpgRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private fun createArticle(id: String, title: String) = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id,
            date = Date(1000L),
            title = title,
            author = "Author",
            shortDescription = "Description",
            img = null,
            link = "https://example.com/$id",
            feedId = "feed1",
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
        feed = Feed(id = "feed1", name = "Test Feed", url = "https://example.com", groupId = "group1", accountId = 1),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadInitialData_populatesArticlesAndCount() = runTest {
        val articles = listOf(createArticle("1", "First"), createArticle("2", "Second"))
        coEvery { articleDao.queryTotalUnreadCount(1) } returns 2
        coEvery { articleDao.queryOldestUnreadArticleListWithFeed(1, 50) } returns articles

        val viewModel = TriageViewModel(
            accountId = 1,
            articleDao = articleDao,
            widgetRefreshNotifier = widgetRefreshNotifier,
            brainRpgRepository = brainRpgRepository,
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.totalCount)
        assertEquals(2, state.articles.size)
        assertEquals("1", state.currentArticle?.article?.id)
        assertEquals("2", state.nextArticle?.article?.id)
        assertFalse(state.isInboxZero)
    }

    @Test
    fun skipCurrent_marksAsRead_andAdvancesToNext() = runTest {
        val articles = listOf(createArticle("1", "First"), createArticle("2", "Second"))
        coEvery { articleDao.queryTotalUnreadCount(1) } returns 2
        coEvery { articleDao.queryOldestUnreadArticleListWithFeed(1, 50) } returns articles
        coEvery { brainRpgRepository.awardTriageXpOnce("1", 5L, any()) } returns 5L

        val viewModel = TriageViewModel(
            accountId = 1,
            articleDao = articleDao,
            widgetRefreshNotifier = widgetRefreshNotifier,
            brainRpgRepository = brainRpgRepository,
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.skipCurrent()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { articleDao.markAsReadByArticleId(1, "1", false) }
        coVerify { widgetRefreshNotifier.refresh() }

        val state = viewModel.uiState.value
        assertEquals(1, state.processedCount)
        assertEquals(1, state.skippedCount)
        assertEquals(0, state.savedCount)
        assertEquals(5L, state.totalXpEarned)
        assertEquals("2", state.currentArticle?.article?.id)
    }

    @Test
    fun saveCurrent_marksAsStarredAndRead_andAdvances() = runTest {
        val articles = listOf(createArticle("1", "First"))
        coEvery { articleDao.queryTotalUnreadCount(1) } returns 1
        coEvery { articleDao.queryOldestUnreadArticleListWithFeed(1, 50) } returns articles
        coEvery { brainRpgRepository.awardTriageXpOnce("1", 5L, any()) } returns 5L

        val viewModel = TriageViewModel(
            accountId = 1,
            articleDao = articleDao,
            widgetRefreshNotifier = widgetRefreshNotifier,
            brainRpgRepository = brainRpgRepository,
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveCurrent()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { articleDao.markAsStarredByArticleId(1, "1", true) }
        coVerify { articleDao.markAsReadByArticleId(1, "1", false) }
        coVerify { widgetRefreshNotifier.refresh() }

        val state = viewModel.uiState.value
        assertEquals(1, state.processedCount)
        assertEquals(1, state.savedCount)
        assertTrue(state.isInboxZero)
    }
}
