package com.mckimquyen.reader.ui.page.share

import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.SystemReadLaterFeedManager
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareTargetViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val feedManager = mockk<SystemReadLaterFeedManager>()
    private val articleDao = mockk<ArticleDao>()
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun extractsUrlAndSetsTargetState() {
        val vm = ShareTargetViewModel(feedManager, articleDao, workManager)
        vm.initFromShareText("Read https://news.test/article-123 now")
        assertEquals("https://news.test/article-123", vm.uiState.value.targetUrl)
    }

    @Test
    fun saveArticleInsertsPlaceholderAndEnqueuesWork() = runTest {
        val feed = Feed(
            id = Feed.READ_LATER_FEED_ID,
            name = "Read Later",
            url = Feed.READ_LATER_URL,
            groupId = "group-1",
            accountId = 1
        )
        coEvery { feedManager.ensureReadLaterFeed(any(), any()) } returns feed
        coEvery { articleDao.insert(any()) } returns Unit

        val vm = ShareTargetViewModel(feedManager, articleDao, workManager)
        vm.initFromShareText("https://news.test/article-123")

        var saved = false
        vm.saveArticle(accountId = 1, defaultTitle = "Read Later") { saved = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.savedArticleId)
        assertEquals(true, saved)
        coVerify(exactly = 1) { articleDao.insert(match { it.link == "https://news.test/article-123" && it.isReadLater }) }
        coVerify(exactly = 1) { workManager.enqueue(any<WorkRequest>()) }
    }
}
