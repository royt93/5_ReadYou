package com.mckimquyen.reader.ui.page.home

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.clustering.StoryClusteringEngine
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchResult
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Regression coverage for FIX-11: HomeViewModel.fetchArticles() must not let a slower, stale
 * search query overwrite the result of a more recently typed query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeViewModelSearchRaceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val clusteringEngine = mockk<StoryClusteringEngine>(relaxed = true)
    private val semanticSearchEngine = mockk<SemanticSearchEngine>(relaxed = true)
    private val rssService = mockk<RssSv>(relaxed = true)
    private val watchdogManager = mockk<WatchdogManager>(relaxed = true)
    private val androidStringsHelper = mockk<AndroidStringsHelper>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private fun result(query: String, id: String): List<SemanticSearchResult> {
        val article = Article(
            id = id,
            date = Date(),
            title = "Result for $query",
            author = "author",
            rawDescription = "",
            shortDescription = "",
            fullContent = "",
            link = "https://example.com/$id",
            feedId = "feed_1",
            accountId = 1,
            isUnread = false,
        )
        val feed = Feed(
            id = "feed_1",
            name = "Feed",
            url = "https://example.com/feed",
            groupId = "group_1",
            accountId = 1,
            isFullContent = false,
        )
        return listOf(SemanticSearchResult(ArticleWithFeed(article, feed), score = 0.9f))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        every { watchdogManager.keywords } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HomeViewModel(
        context = context,
        articleDao = articleDao,
        clusteringEngine = clusteringEngine,
        semanticSearchEngine = semanticSearchEngine,
        rssService = rssService,
        watchdogManager = watchdogManager,
        androidStringsHelper = androidStringsHelper,
        applicationScope = CoroutineScope(SupervisorJob()),
        workManager = workManager,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun inputSearchContent_staleSlowQuery_doesNotOverwriteNewerQueryResult() = runTest(testDispatcher) {
        // First keystroke "a": debounce 300ms, then a slow 500ms DB read.
        // Second keystroke "ab" arrives at t=400ms, i.e. AFTER the first query's debounce
        // has already elapsed and it is mid-flight on the (mocked) DB read.
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), 200) } coAnswers {
            delay(500)
            emptyList()
        }
        every { semanticSearchEngine.rank("a", any(), any(), any()) } returns result("a", "art_a")
        every { semanticSearchEngine.rank("ab", any(), any(), any()) } returns result("ab", "art_ab")

        val viewModel = buildViewModel()

        viewModel.inputSearchContent("a")
        advanceTimeBy(400) // debounce (300ms) elapses; first query is now awaiting the DB read
        runCurrent()

        viewModel.inputSearchContent("ab") // must cancel the still in-flight "a" query
        advanceUntilIdle()

        // The stale "a" query must never have reached rank(): it was cancelled mid-flight.
        verify(exactly = 0) { semanticSearchEngine.rank("a", any(), any(), any()) }
        verify(exactly = 1) { semanticSearchEngine.rank("ab", any(), any(), any()) }

        val finalResults = viewModel.semanticSearchResults.value
        assertEquals(1, finalResults.size)
        assertEquals("art_ab", finalResults.first().articleWithFeed.article.id)
    }

    @Test
    fun inputSearchContent_rapidTyping_debouncesToASingleQuery() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), 200) } returns emptyList()
        every { semanticSearchEngine.rank(any(), any(), any(), any()) } answers {
            result(firstArg(), "art_final")
        }

        val viewModel = buildViewModel()

        // Simulate fast typing: each keystroke arrives well within the 300ms debounce window.
        viewModel.inputSearchContent("a")
        advanceTimeBy(50)
        viewModel.inputSearchContent("ab")
        advanceTimeBy(50)
        viewModel.inputSearchContent("abc")
        advanceUntilIdle()

        // Only the last keystroke should ever have triggered a real DB query / rank() call.
        coVerify(exactly = 1) { articleDao.queryRecentArticlesWithFeed(any(), 200) }
        verify(exactly = 1) { semanticSearchEngine.rank("abc", any(), any(), any()) }
        assertEquals("art_final", viewModel.semanticSearchResults.value.first().articleWithFeed.article.id)
    }

    @Test
    fun changeFilter_isNotDebounced_appliesImmediately() = runTest(testDispatcher) {
        coEvery { articleDao.queryRecentArticlesWithFeed(any(), 150) } returns emptyList()

        val viewModel = buildViewModel()
        viewModel.changeFilter(FilterState())
        // changeFilter must not wait out a debounce window before its query starts.
        runCurrent()

        coVerify(exactly = 1) { articleDao.queryRecentArticlesWithFeed(any(), 150) }
    }
}
