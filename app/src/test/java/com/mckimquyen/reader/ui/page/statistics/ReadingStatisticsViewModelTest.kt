package com.mckimquyen.reader.ui.page.statistics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.statistics.FeedReadCount
import com.mckimquyen.reader.domain.model.statistics.ReadingDayCount
import com.mckimquyen.reader.domain.model.statistics.ReadingStatisticsCalendar
import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReadingStatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val articleDao: ArticleDao = mockk()
    private val calendar: ReadingStatisticsCalendar = mockk()
    private lateinit var app: Application
    private val fixedToday = LocalDate.of(2026, 10, 18)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        every { calendar.today() } returns fixedToday
        every { calendar.windowStartMillis() } returns WINDOW_START
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_emitsLoadingThenEmptyWhenTotalReadIsZero() = runTest(testDispatcher) {
        coEvery { articleDao.queryTotalRead(1) } returns 0

        val vm = ReadingStatisticsViewModel(app, articleDao, calendar, testDispatcher)
        assertEquals(ReadingStatisticsUiState.Loading, vm.state.value)

        advanceUntilIdle()
        assertEquals(ReadingStatisticsUiState.Empty, vm.state.value)
        coVerify(exactly = 0) { articleDao.queryReadingDayCounts(any(), any()) }
        coVerify(exactly = 0) { articleDao.queryTopFeeds(any(), any()) }
    }

    @Test
    fun init_emitsContentWithAssembledStatsWhenRowsPresent() = runTest(testDispatcher) {
        coEvery { articleDao.queryTotalRead(1) } returns 10
        coEvery { articleDao.queryReadingDayCounts(1, WINDOW_START) } returns listOf(
            ReadingDayCount("2026-10-18", 3),
            ReadingDayCount("2026-10-17", 1),
        )
        val top = listOf(FeedReadCount("f1", "Feed 1", 4))
        coEvery { articleDao.queryTopFeeds(1, 5) } returns top

        val vm = ReadingStatisticsViewModel(app, articleDao, calendar, testDispatcher)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Content but got $state", state is ReadingStatisticsUiState.Content)
        val stats = (state as ReadingStatisticsUiState.Content).stats
        assertEquals(10, stats.totalRead)
        assertEquals(3, stats.readToday)
        assertEquals(2, stats.currentStreak)
        assertEquals(top, stats.topFeeds)
    }

    @Test
    fun init_emitsErrorWhenDaoThrowsWithoutCrashing() = runTest(testDispatcher) {
        coEvery { articleDao.queryTotalRead(any()) } throws IOException("disk read error")

        val vm = ReadingStatisticsViewModel(app, articleDao, calendar, testDispatcher)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Error but got $state", state is ReadingStatisticsUiState.Error)
        assertEquals(R.string.reading_stats_error, (state as ReadingStatisticsUiState.Error).messageRes)
    }

    @Test
    fun refresh_updatesStatsWhenCalendarChanges() = runTest(testDispatcher) {
        coEvery { articleDao.queryTotalRead(1) } returns 5
        coEvery { articleDao.queryReadingDayCounts(1, any()) } returns listOf(
            ReadingDayCount("2026-10-18", 2),
            ReadingDayCount("2026-10-19", 1),
        )
        coEvery { articleDao.queryTopFeeds(1, any()) } returns emptyList()

        val vm = ReadingStatisticsViewModel(app, articleDao, calendar, testDispatcher)
        advanceUntilIdle()
        assertEquals(2, (vm.state.value as ReadingStatisticsUiState.Content).stats.readToday)

        every { calendar.today() } returns LocalDate.of(2026, 10, 19)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, (vm.state.value as ReadingStatisticsUiState.Content).stats.readToday)
    }

    @Test
    fun refresh_whileFirstRunIsSuspended_cancelsItAndPublishesOnlyLatestResult() = runTest(testDispatcher) {
        // Lần truy vấn đầu treo ở DAO cho tới khi mở cổng; lần sau trả ngay total = 5.
        val gate = CompletableDeferred<Int>()
        var calls = 0
        coEvery { articleDao.queryTotalRead(1) } coAnswers { if (calls++ == 0) gate.await() else LATEST_TOTAL }
        coEvery { articleDao.queryReadingDayCounts(1, any()) } returns listOf(ReadingDayCount("2026-10-18", 1))
        coEvery { articleDao.queryTopFeeds(1, any()) } returns emptyList()

        val vm = ReadingStatisticsViewModel(app, articleDao, calendar, testDispatcher)
        runCurrent() // job 1 bắt đầu chạy và treo tại gate.await()
        assertEquals(1, calls)

        vm.refresh() // huỷ job 1 đang treo, chạy job 2
        advanceUntilIdle()
        assertEquals(LATEST_TOTAL, (vm.state.value as ReadingStatisticsUiState.Content).stats.totalRead)

        gate.complete(0) // kết quả muộn của job 1 (Empty) không được ghi đè
        advanceUntilIdle()
        assertEquals(LATEST_TOTAL, (vm.state.value as ReadingStatisticsUiState.Content).stats.totalRead)
    }

    private companion object {
        const val WINDOW_START = 1_000L
        const val LATEST_TOTAL = 5
    }
}
