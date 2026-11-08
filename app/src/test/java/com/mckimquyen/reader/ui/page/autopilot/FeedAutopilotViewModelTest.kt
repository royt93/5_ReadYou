package com.mckimquyen.reader.ui.page.autopilot

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotDecisionsCodec
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotEvaluation
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotSuggestion
import com.mckimquyen.reader.domain.autopilot.FeedDecisionType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.sv.FeedAutopilotProcessor
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.feedAutopilotEnabled
import com.mckimquyen.reader.ui.ext.feedAutopilotPendingCount
import com.mckimquyen.reader.ui.ext.get
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedAutopilotViewModelTest {

    // Only used so viewModelScope can be created. The ViewModel itself runs on Dispatchers.Unconfined: DataStore.put
    // hops through Dispatchers.IO inside edit{}, and resuming onto a test scheduler while the test thread is blocked
    // in dataStore.get (runBlocking) would never complete, so tests wait on real conditions instead.
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val processor: FeedAutopilotProcessor = mockk()
    private val articleDao: ArticleDao = mockk()
    private val feedDao: FeedDao = mockk()
    private val localRssSv: LocalRssSv = mockk(relaxed = true)
    private val rssSv: RssSv = mockk()
    private lateinit var context: Context

    private val feedA = Feed(id = "fa", name = "Feed A", url = "https://e.com/a", groupId = "g", accountId = ACCOUNT)
    private val feedB = Feed(id = "fb", name = "Feed B", url = "https://e.com/b", groupId = "g", accountId = ACCOUNT)

    private fun suggestion(feed: Feed) = FeedAutopilotSuggestion(
        feedId = feed.id, feedName = feed.name, feedIcon = null, windowTotal = 30, windowEngaged = 0,
        engagementRatio = 0f, engagementPercent = 0, windowDays = 30, feedAgeDays = 20,
    )

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, ACCOUNT)
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotEnabled, true)
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotDecisions, "")
        // Non-zero on purpose: tests wait for this value to change, which proves the write really happened.
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotPendingCount, SENTINEL_PENDING)
        Dispatchers.setMain(mainDispatcher)
        every { rssSv.get() } returns localRssSv
        coEvery { feedDao.queryById("fa") } returns feedA
        coEvery { feedDao.queryById("fb") } returns feedB
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun ready(vararg feeds: Feed) {
        coEvery { processor.computeSuggestions(any()) } returns
            FeedAutopilotEvaluation.Ready(feeds.map(::suggestion), WINDOW_DAYS)
    }

    private fun viewModel() = FeedAutopilotViewModel(context, processor, rssSv, articleDao, feedDao, Dispatchers.Unconfined)

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(10)
        }
    }

    private fun storedDecisions() = FeedAutopilotDecisionsCodec.decode(
        context.dataStore.get(DataStoreKeys.FeedAutopilotDecisions), System.currentTimeMillis(), null,
    )

    private fun awaitState(vm: FeedAutopilotViewModel, predicate: (FeedAutopilotUiState) -> Boolean) = runBlocking {
        withTimeout(5_000) { vm.state.first(predicate) }
    }

    @Test fun startsLoadingThenShowsSuggestions() {
        val gate = CompletableDeferred<FeedAutopilotEvaluation>()
        coEvery { processor.computeSuggestions(any()) } coAnswers { gate.await() }
        val vm = viewModel()
        assertEquals(FeedAutopilotUiState.Loading, vm.state.value)
        gate.complete(FeedAutopilotEvaluation.Ready(listOf(suggestion(feedA), suggestion(feedB)), WINDOW_DAYS))
        val state = awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions } as FeedAutopilotUiState.HasSuggestions
        assertEquals(listOf("fa", "fb"), state.suggestions.map { it.feedId })
        assertEquals(WINDOW_DAYS, state.windowDays)
    }

    @Test fun noSuggestionsMeansClean() {
        ready()
        val vm = viewModel()
        assertEquals(FeedAutopilotUiState.Clean(WINDOW_DAYS), awaitState(vm) { it !is FeedAutopilotUiState.Loading })
    }

    @Test fun eachNonReadyOutcomeMapsToItsOwnState() {
        val cases = mapOf(
            FeedAutopilotEvaluation.Disabled to FeedAutopilotUiState.Disabled,
            FeedAutopilotEvaluation.NotLocalAccount to FeedAutopilotUiState.NotLocalAccount,
            FeedAutopilotEvaluation.RetentionTooShort to FeedAutopilotUiState.RetentionTooShort,
        )
        for ((evaluation, expected) in cases) {
            coEvery { processor.computeSuggestions(any()) } returns evaluation
            assertEquals(expected, awaitState(viewModel()) { it !is FeedAutopilotUiState.Loading })
        }
    }

    @Test fun processorFailureShowsErrorInsteadOfCrashing() {
        coEvery { processor.computeSuggestions(any()) } throws IOException("disk")
        assertEquals(FeedAutopilotUiState.Error, awaitState(viewModel()) { it !is FeedAutopilotUiState.Loading })
    }

    @Test fun snoozeStoresADecisionRemovesTheCardAndLowersThePendingCount() {
        ready(feedA, feedB)
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.snoozeFeed("fa")
        awaitUntil("pending count to drop to 1") { context.feedAutopilotPendingCount == 1 }

        val state = vm.state.value as FeedAutopilotUiState.HasSuggestions
        assertEquals(listOf("fb"), state.suggestions.map { it.feedId })
        assertEquals(FeedDecisionType.SNOOZE_30_DAYS, storedDecisions().getValue("fa").type)
    }

    @Test fun keepStoresKeepForeverAndRemovesTheCard() {
        ready(feedA)
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.keepFeed("fa")
        awaitUntil("pending count to drop to 0") { context.feedAutopilotPendingCount == 0 }

        assertEquals(FeedAutopilotUiState.Clean(WINDOW_DAYS), vm.state.value)
        assertEquals(FeedDecisionType.KEEP_FOREVER, storedDecisions().getValue("fa").type)
    }

    @Test fun requestUnsubscribeLoadsTheStarredCountAndDeletesNothing() {
        ready(feedA)
        coEvery { articleDao.queryStarredCountByFeedId(ACCOUNT, "fa") } returns 3
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.requestUnsubscribe("fa")
        awaitUntil("confirmation dialog data") { vm.pendingUnsubscribe.value != null }

        assertEquals(PendingUnsubscribe("fa", "Feed A", 3), vm.pendingUnsubscribe.value)
        coVerify(exactly = 0) { localRssSv.deleteFeed(any()) }
    }

    @Test fun cancelClosesTheDialogAndNeverDeletes() {
        ready(feedA)
        coEvery { articleDao.queryStarredCountByFeedId(any(), any()) } returns 0
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.requestUnsubscribe("fa")
        awaitUntil("confirmation dialog data") { vm.pendingUnsubscribe.value != null }
        vm.cancelUnsubscribe()

        assertNull(vm.pendingUnsubscribe.value)
        coVerify(exactly = 0) { localRssSv.deleteFeed(any()) }
    }

    @Test fun confirmDeletesThroughRssSvRemovesTheCardAndClearsItsDecision() {
        ready(feedA, feedB)
        coEvery { articleDao.queryStarredCountByFeedId(any(), any()) } returns 1
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.snoozeFeed("fa") // a stale decision for ANOTHER feed must be untouched by deleting fb
        awaitUntil("pending count to drop to 1") { context.feedAutopilotPendingCount == 1 }
        vm.requestUnsubscribe("fb")
        awaitUntil("confirmation dialog data") { vm.pendingUnsubscribe.value != null }
        vm.confirmUnsubscribe()
        awaitUntil("pending count to drop to 0") { context.feedAutopilotPendingCount == 0 }

        coVerify(exactly = 1) { localRssSv.deleteFeed(feedB) }
        assertNull(vm.pendingUnsubscribe.value)
        assertEquals(FeedAutopilotUiState.Clean(WINDOW_DAYS), vm.state.value)
        assertEquals(setOf("fa"), storedDecisions().keys)
    }

    @Test fun deletingAFeedAlsoClearsItsOwnStoredDecision() {
        ready(feedA, feedB)
        coEvery { articleDao.queryStarredCountByFeedId(any(), any()) } returns 0
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.keepFeed("fb")
        awaitUntil("pending count to drop to 1") { context.feedAutopilotPendingCount == 1 }
        assertEquals(setOf("fb"), storedDecisions().keys)
        // fb is no longer shown, but the user can still delete it later; its decision must disappear with it.
        vm.requestUnsubscribe("fb")
        awaitUntil("confirmation dialog data") { vm.pendingUnsubscribe.value != null }
        vm.confirmUnsubscribe()
        awaitUntil("decision of fb to be cleared") { storedDecisions().isEmpty() }
        coVerify(exactly = 1) { localRssSv.deleteFeed(feedB) }
    }

    @Test fun confirmWithoutARequestDeletesNothing() {
        ready(feedA)
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.confirmUnsubscribe()
        coVerify(exactly = 0) { localRssSv.deleteFeed(any()) }
    }

    @Test fun deleteFailureKeepsTheCardAndReportsError() {
        ready(feedA)
        coEvery { articleDao.queryStarredCountByFeedId(any(), any()) } returns 0
        coEvery { localRssSv.deleteFeed(any()) } throws IOException("disk")
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        vm.requestUnsubscribe("fa")
        awaitUntil("confirmation dialog data") { vm.pendingUnsubscribe.value != null }
        vm.confirmUnsubscribe()
        awaitState(vm) { it is FeedAutopilotUiState.Error }

        assertNull(vm.pendingUnsubscribe.value)
        assertEquals(SENTINEL_PENDING, context.feedAutopilotPendingCount) // nothing was removed, nothing was counted
    }

    @Test fun togglingOffStopsAnalysisAndShowsDisabled() {
        ready(feedA)
        val vm = viewModel()
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }
        coEvery { processor.computeSuggestions(any()) } returns FeedAutopilotEvaluation.Disabled
        vm.toggleEnabled(false)
        awaitState(vm) { it == FeedAutopilotUiState.Disabled }

        assertEquals(false, context.feedAutopilotEnabled)
    }

    @Test fun togglingOnRunsTheAnalysisAgain() {
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotEnabled, false)
        coEvery { processor.computeSuggestions(any()) } returns FeedAutopilotEvaluation.Disabled
        val vm = viewModel()
        awaitState(vm) { it == FeedAutopilotUiState.Disabled }
        ready(feedA)
        vm.toggleEnabled(true)
        awaitState(vm) { it is FeedAutopilotUiState.HasSuggestions }

        assertEquals(true, context.feedAutopilotEnabled)
    }

    private companion object {
        const val ACCOUNT = 1
        const val WINDOW_DAYS = 30
        const val SENTINEL_PENDING = 5
    }
}
