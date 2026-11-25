package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer
import com.mckimquyen.reader.domain.model.rpg.StreakRescuePolicy
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StreakRescueViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val day = StreakRescuePolicy.MILLIS_PER_DAY
    private var now = 20_000L * day
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private val promptDayStore = FakeStreakRescuePromptDayStore()
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }

    private class FakeStreakRescuePromptDayStore : StreakRescuePromptDayStore {
        var day: Long = -1L
        override suspend fun readLastPromptDay(): Long = day
        override suspend fun writeLastPromptDay(epochDay: Long) { day = epochDay }
    }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            // Direct executors: Room work completes inline so advanceUntilIdle() really waits for it.
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        repository = BrainRpgRepository(context, database, dao, importer, newScope(), Dispatchers.Unconfined)
        // The streak day is the local day; this test computes days as now / day (a UTC day), so it pins UTC.
        repository.zone = { java.time.ZoneOffset.UTC }
        promptDayStore.day = -1L
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        adLauncher: (Activity, (Boolean) -> Unit) -> Unit = { _, cb -> cb(true) },
    ) = StreakRescueViewModel(
        repository = repository,
        promptDayStore = promptDayStore,
        defaultDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        clock = { now },
        adLauncher = adLauncher,
    )

    private suspend fun seedBrokenStreak(streak: Int = 5, daysAgo: Long = 3) {
        val lastRead = (now / day) - daysAgo
        dao.upsertProfile(
            BrainRpgProfileEntity(
                id = 1,
                streakDays = streak,
                lastReadEpochDay = lastRead,
                streakShieldActive = false,
                legacyImportComplete = true,
            ),
        )
        repository.refresh(now)
    }

    @Test fun checkOnStartup_promptsWhenStreakIsBroken_butDoesNotYetSaveThePromptDay() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()

        vm.checkOnStartup()
        advanceUntilIdle()

        val prompt = vm.state.value as StreakRescueUiState.Prompt
        assertEquals(StreakRescueOffer(streakDays = 5, missedDays = 3), prompt.offer)
        assertFalse(prompt.adFailed)
        assertFalse(prompt.isLoadingAd)
        assertEquals("shown is not answered: the mark must wait for the user", -1L, promptDayStore.readLastPromptDay())
    }

    @Test fun aPromptThatWasShownButNeverAnswered_isOfferedAgainAfterARestart() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        createViewModel().apply { checkOnStartup() }
        advanceUntilIdle()
        // The process dies here. A new view model must offer the rescue again the same day.
        val vm2 = createViewModel()
        vm2.checkOnStartup()
        advanceUntilIdle()
        assertTrue(vm2.state.value is StreakRescueUiState.Prompt)
    }

    @Test fun onDismissLater_savesThePromptDay_soItIsNotAskedAgainToday() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onDismissLater()
        advanceUntilIdle()

        assertEquals(now / day, promptDayStore.readLastPromptDay())
        val vm2 = createViewModel()
        vm2.checkOnStartup()
        advanceUntilIdle()
        assertEquals(StreakRescueUiState.Idle, vm2.state.value)
    }

    @Test fun onRestartFromScratch_savesThePromptDay() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRestartFromScratch()
        advanceUntilIdle()

        assertEquals(now / day, promptDayStore.readLastPromptDay())
    }

    @Test fun aRescueThatWorked_savesThePromptDay() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel { _, cb -> cb(true) }
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk(relaxed = true))
        advanceUntilIdle()

        assertEquals(now / day, promptDayStore.readLastPromptDay())
    }

    @Test fun aRescueWhoseAdFailed_doesNotSaveThePromptDay_soTheUserCanTryAgain() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel { _, cb -> cb(false) }
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk(relaxed = true))
        advanceUntilIdle()

        assertTrue((vm.state.value as StreakRescueUiState.Prompt).adFailed)
        assertEquals(-1L, promptDayStore.readLastPromptDay())
    }

    @Test fun checkOnStartup_staysIdleWhenStreakIsAlive() = runTest(testDispatcher) {
        val yesterday = (now / day) - 1
        dao.upsertProfile(
            BrainRpgProfileEntity(
                id = 1,
                streakDays = 5,
                lastReadEpochDay = yesterday,
                streakShieldActive = false,
                legacyImportComplete = true,
            ),
        )
        repository.refresh(now)

        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
    }

    @Test fun checkOnStartup_staysIdleWhenAlreadyPromptedToday() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        promptDayStore.writeLastPromptDay(now / day)

        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
    }

    @Test fun onRestartFromScratch_resetsStreakAndDismisses() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRestartFromScratch()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
        assertEquals(0, repository.getProgress().streakDays)
    }

    @Test fun onDismissLater_dismissesWithoutResettingStreak() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onDismissLater()
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
        assertEquals(5, repository.getProgress().streakDays)
    }

    @Test fun onRescueWithAd_onSuccess_armsShieldAndDismisses() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel(adLauncher = { _, cb -> cb(true) })
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk())
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
        assertTrue(repository.getProgress().streakShieldActive)
        assertEquals(5, repository.getProgress().streakDays)
    }

    @Test fun onRescueWithAd_onFailure_retainsPromptWithErrorMessage() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel(adLauncher = { _, cb -> cb(false) })
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk())
        advanceUntilIdle()

        val prompt = vm.state.value as StreakRescueUiState.Prompt
        assertTrue(prompt.adFailed)
        assertFalse(prompt.isLoadingAd)
        assertFalse(repository.getProgress().streakShieldActive)
    }

    /**
     * The ad SDK answers `earned=false` on every path where no ad is shown (cooldown, safety block, not ready, no network), so the only state the
     * app has to survive is "waiting for the callback". While it waits the dialog must not start a second ad or let the user leave a half state,
     * and once the callback says no, every button has to work again.
     */
    @Test fun onRescueWithAd_whileWaiting_ignoresASecondTapAndTheOtherButtons_thenWorksAgainAfterAFailure() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val pending = mutableListOf<(Boolean) -> Unit>()
        val vm = createViewModel(adLauncher = { _, cb -> pending += cb })
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk())
        vm.onRescueWithAd(mockk())
        vm.onDismissLater()
        vm.onRestartFromScratch()
        advanceUntilIdle()

        assertEquals("a second tap must not start a second ad", 1, pending.size)
        val waiting = vm.state.value as StreakRescueUiState.Prompt
        assertTrue(waiting.isLoadingAd)
        assertEquals("the streak must not be reset while the ad is pending", 5, repository.getProgress().streakDays)

        pending.single()(false)
        advanceUntilIdle()

        val failed = vm.state.value as StreakRescueUiState.Prompt
        assertFalse(failed.isLoadingAd)
        assertTrue(failed.adFailed)

        vm.onRescueWithAd(mockk())
        advanceUntilIdle()
        assertEquals("after a failure the user can try again", 2, pending.size)
    }

    @Test fun onRescueWithAd_retryAfterAFailure_canStillRescue() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        var answers = listOf(false, true).iterator()
        val vm = createViewModel(adLauncher = { _, cb -> cb(answers.next()) })
        vm.checkOnStartup()
        advanceUntilIdle()

        vm.onRescueWithAd(mockk())
        advanceUntilIdle()
        assertTrue((vm.state.value as StreakRescueUiState.Prompt).adFailed)

        vm.onRescueWithAd(mockk())
        advanceUntilIdle()

        assertEquals(StreakRescueUiState.Idle, vm.state.value)
        assertTrue(repository.getProgress().streakShieldActive)
    }
}
