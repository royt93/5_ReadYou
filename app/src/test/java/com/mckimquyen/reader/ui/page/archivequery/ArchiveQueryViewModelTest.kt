package com.mckimquyen.reader.ui.page.archivequery

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryDateRange
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryResult
import com.mckimquyen.reader.domain.archivequery.ParsedArchiveQuery
import com.mckimquyen.reader.infrastructure.archivequery.ArchiveQueryRepository
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArchiveQueryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: ArchiveQueryRepository = mockk()
    private val now = Instant.parse("2026-11-07T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val defaultRange = ArchiveQueryDateRange.default(now, ZoneOffset.UTC)
    private lateinit var context: Context

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, ACCOUNT)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel() = ArchiveQueryViewModel(context, repository, testDispatcher, clock)

    private fun result(total: Int, range: ArchiveQueryRange = defaultRange) =
        ArchiveQueryResult(range = range, months = emptyList(), shifts = emptyList(), totalRelevantArticles = total)

    private fun stubSearch(total: Int = 1) {
        coEvery { repository.search(any(), any(), any(), any(), any()) } returns result(total)
    }

    @Test fun startsIdle() = runTest(testDispatcher) {
        assertEquals(ArchiveQueryUiState.Idle, viewModel().state.value)
    }

    @Test fun validQuestion_goesSearchingThenReady_usingDefaultRangeAndCurrentAccount() = runTest(testDispatcher) {
        stubSearch()
        val vm = viewModel()
        vm.submit("interest rates", null, null)
        assertEquals(ArchiveQueryUiState.Searching("interest rates", defaultRange), vm.state.value)
        advanceUntilIdle()
        val ready = vm.state.value as ArchiveQueryUiState.Ready
        assertEquals("interest rates", ready.topic)
        coVerify(exactly = 1) {
            repository.search(ACCOUNT, ParsedArchiveQuery("interest rates", null, false), defaultRange, now, ZoneOffset.UTC)
        }
    }

    @Test fun noRelevantArticles_isNoResultsNotReady() = runTest(testDispatcher) {
        stubSearch(total = 0)
        val vm = viewModel()
        vm.submit("interest rates", null, null)
        advanceUntilIdle()
        assertEquals(ArchiveQueryUiState.NoResults("interest rates", defaultRange), vm.state.value)
    }

    @Test fun blankQuestion_returnsToIdleAndDoesNotSearch() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.submit("   ", null, null)
        advanceUntilIdle()
        assertEquals(ArchiveQueryUiState.Idle, vm.state.value)
        coVerify(exactly = 0) { repository.search(any(), any(), any(), any(), any()) }
    }

    @Test fun blankQuestion_cancelsTheRunningSearchAndDropsItsResult() = runTest(testDispatcher) {
        val gate = CompletableDeferred<ArchiveQueryResult>()
        coEvery { repository.search(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val vm = viewModel()
        vm.submit("interest rates", null, null)
        advanceUntilIdle()
        vm.submit("", null, null)
        gate.complete(result(5))
        advanceUntilIdle()
        assertEquals(ArchiveQueryUiState.Idle, vm.state.value)
    }

    @Test fun tooLongQuestion_isInvalid() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.submit("a".repeat(501), null, null)
        assertEquals(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.TooLong), vm.state.value)
    }

    @Test fun questionThatIsOnlyADateClause_isBlankTopic() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.submit("since January", null, null)
        assertEquals(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.BlankTopic), vm.state.value)
    }

    @Test fun unsupportedTimePhrase_asksForDates_andNeverSearchesWithTheDefault() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.submit("AI last 3 months", null, null)
        advanceUntilIdle()
        assertEquals(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.NeedsDateClarification), vm.state.value)
        coVerify(exactly = 0) { repository.search(any(), any(), any(), any(), any()) }
    }

    @Test fun supportedDateClauseInQuestion_isUsedAsTheRange() = runTest(testDispatcher) {
        stubSearch()
        val vm = viewModel()
        vm.submit("interest rates since March", null, null)
        advanceUntilIdle()
        val expected = ArchiveQueryRange(Instant.parse("2026-03-01T00:00:00Z"), now)
        coVerify { repository.search(ACCOUNT, ParsedArchiveQuery("interest rates", expected, false), expected, now, ZoneOffset.UTC) }
    }

    @Test fun explicitDates_winOverTheDateClauseInTheQuestion() = runTest(testDispatcher) {
        stubSearch()
        val vm = viewModel()
        vm.submit("interest rates since March", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        advanceUntilIdle()
        val expected = ArchiveQueryRange(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"))
        coVerify { repository.search(ACCOUNT, any(), expected, now, ZoneOffset.UTC) }
    }

    @Test fun explicitDatesResolveAnUnsupportedTimePhrase() = runTest(testDispatcher) {
        stubSearch()
        val vm = viewModel()
        vm.submit("AI last 3 months", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31))
        advanceUntilIdle()
        assertTrue(vm.state.value is ArchiveQueryUiState.Ready)
    }

    @Test fun invalidExplicitRanges_areRejectedWithTheRightProblem() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.submit("AI", LocalDate.of(2026, 9, 1), null)
        assertEquals(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.IncompleteRange), vm.state.value)
        vm.submit("AI", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1))
        assertEquals(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.RangeReversed), vm.state.value)
        vm.submit("AI", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1))
        assertEquals(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.RangeInFuture), vm.state.value)
        vm.submit("AI", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 1))
        assertEquals(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.RangeTooWide), vm.state.value)
        coVerify(exactly = 0) { repository.search(any(), any(), any(), any(), any()) }
    }

    @Test fun repositoryFailure_isErrorAndRetryRunsTheSameRequestAgain() = runTest(testDispatcher) {
        coEvery { repository.search(any(), any(), any(), any(), any()) } throws IOException("disk")
        val vm = viewModel()
        vm.submit("interest rates", null, null)
        advanceUntilIdle()
        assertEquals(ArchiveQueryUiState.Error, vm.state.value)

        stubSearch()
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.state.value is ArchiveQueryUiState.Ready)
        coVerify(exactly = 2) { repository.search(ACCOUNT, ParsedArchiveQuery("interest rates", null, false), defaultRange, now, ZoneOffset.UTC) }
    }

    @Test fun retryWithoutAPreviousRequest_doesNothing() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.retry()
        advanceUntilIdle()
        assertEquals(ArchiveQueryUiState.Idle, vm.state.value)
        coVerify(exactly = 0) { repository.search(any(), any(), any(), any(), any()) }
    }

    @Test fun cancellationInsideTheRepository_isNeverReportedAsAnError() = runTest(testDispatcher) {
        coEvery { repository.search(any(), any(), any(), any(), any()) } throws CancellationException("stop")
        val vm = viewModel()
        vm.submit("interest rates", null, null)
        advanceUntilIdle()
        assertNotEquals(ArchiveQueryUiState.Error, vm.state.value)
    }

    @Test fun latestSubmitWins_evenWhenAnOlderSearchFinishesLast() = runTest(testDispatcher) {
        val slow = CompletableDeferred<ArchiveQueryResult>()
        coEvery { repository.search(any(), match { it.topic == "old topic" }, any(), any(), any()) } coAnswers { slow.await() }
        coEvery { repository.search(any(), match { it.topic == "new topic" }, any(), any(), any()) } returns result(2)
        val vm = viewModel()
        vm.submit("old topic", null, null)
        advanceUntilIdle()
        vm.submit("new topic", null, null)
        advanceUntilIdle()
        slow.complete(result(9))
        advanceUntilIdle()
        val ready = vm.state.value as ArchiveQueryUiState.Ready
        assertEquals("new topic", ready.topic)
        assertEquals(2, ready.result.totalRelevantArticles)
    }

    @Test fun staleResultFromANonCancellableSearch_neverOverwritesTheNewerState() = runTest(testDispatcher) {
        // A repository that ignores cancellation: cancel() alone cannot stop it, only the generation guard can.
        val slow = CompletableDeferred<ArchiveQueryResult>()
        coEvery { repository.search(any(), match { it.topic == "old topic" }, any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { slow.await() }
        }
        coEvery { repository.search(any(), match { it.topic == "new topic" }, any(), any(), any()) } returns result(2)
        val vm = viewModel()
        vm.submit("old topic", null, null)
        advanceUntilIdle()
        vm.submit("new topic", null, null)
        advanceUntilIdle()
        slow.complete(result(9))
        advanceUntilIdle()
        val ready = vm.state.value as ArchiveQueryUiState.Ready
        assertEquals("new topic", ready.topic)
        assertEquals(2, ready.result.totalRelevantArticles)
    }

    @Test fun staleFailureFromANonCancellableSearch_isNotShownAsAnError() = runTest(testDispatcher) {
        val slow = CompletableDeferred<ArchiveQueryResult>()
        coEvery { repository.search(any(), match { it.topic == "old topic" }, any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { slow.await() }
        }
        coEvery { repository.search(any(), match { it.topic == "new topic" }, any(), any(), any()) } returns result(2)
        val vm = viewModel()
        vm.submit("old topic", null, null)
        advanceUntilIdle()
        vm.submit("new topic", null, null)
        advanceUntilIdle()
        slow.completeExceptionally(IOException("late failure"))
        advanceUntilIdle()
        assertTrue(vm.state.value is ArchiveQueryUiState.Ready)
    }

    @Test fun accountSwitchedDuringSearch_dropsTheStaleResult() = runTest(testDispatcher) {
        coEvery { repository.search(any(), any(), any(), any(), any()) } coAnswers {
            context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, OTHER_ACCOUNT)
            result(3)
        }
        val vm = viewModel()
        vm.submit("interest rates", null, null)
        advanceUntilIdle()
        assertEquals(ArchiveQueryUiState.Idle, vm.state.value)
    }

    private companion object {
        const val ACCOUNT = 1
        const val OTHER_ACCOUNT = 2
    }
}
