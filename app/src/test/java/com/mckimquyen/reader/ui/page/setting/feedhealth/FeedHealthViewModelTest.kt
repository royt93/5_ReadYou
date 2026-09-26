package com.mckimquyen.reader.ui.page.setting.feedhealth

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedErrorType
import com.mckimquyen.reader.domain.model.feed.FeedHealthRecord
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.group.GroupWithFeed
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedHealthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>(relaxed = true)
    private val repo = mockk<AbstractRssRepository>(relaxed = true)
    private val feedDao = mockk<FeedDao>(relaxed = true)
    private val groupDao = mockk<GroupDao>(relaxed = true)
    private val feedHealthDao = mockk<FeedHealthDao>(relaxed = true)
    private lateinit var context: Context

    private val feedHealthy = Feed(id = "f_healthy", name = "A Healthy Feed", url = "https://a.test/rss", groupId = "g1", accountId = 1)
    private val feedFailing = Feed(id = "f_failing", name = "Z Failing Feed", url = "https://z.test/rss", groupId = "g1", accountId = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        every { rssService.get() } returns repo

        val groupWithFeeds = listOf(
            GroupWithFeed(
                group = Group(id = "g1", name = "News", accountId = 1),
                feeds = mutableListOf(feedHealthy, feedFailing),
            )
        )
        every { groupDao.queryAllGroupWithFeedAsFlow(any()) } returns flowOf(groupWithFeeds.toMutableList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_sortsFailingFeedsToTop() = runTest(testDispatcher) {
        val records = listOf(
            FeedHealthRecord(feedId = "f_healthy", lastErrorType = FeedErrorType.NONE),
            FeedHealthRecord(feedId = "f_failing", lastErrorType = FeedErrorType.TIMEOUT, lastErrorMessage = "Timed out"),
        )
        every { feedHealthDao.observeAll() } returns flowOf(records)

        val viewModel = FeedHealthViewModel(context, rssService, feedDao, groupDao, feedHealthDao)
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(2, items.size)
        // Failing feed should be first despite "Z" starting letter
        assertEquals("f_failing", items[0].feed.id)
        assertEquals(true, items[0].record?.isFailing)
        assertEquals("f_healthy", items[1].feed.id)
        assertEquals(false, items[1].record?.isFailing)
    }

    @Test
    fun retryFeed_callsRepoRetrySync() = runTest(testDispatcher) {
        every { feedHealthDao.observeAll() } returns flowOf(emptyList())
        coEvery { feedDao.queryById("f_failing") } returns feedFailing
        coEvery { repo.retryFeedSync(feedFailing) } returns true

        val viewModel = FeedHealthViewModel(context, rssService, feedDao, groupDao, feedHealthDao)
        advanceUntilIdle()

        viewModel.retryFeed("f_failing")
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.retryFeedSync(feedFailing) }
    }
}
