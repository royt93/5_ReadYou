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

    @Test fun checkOnStartup_promptsWhenStreakIsBroken_andSavesPromptDay() = runTest(testDispatcher) {
        seedBrokenStreak(streak = 5, daysAgo = 3)
        val vm = createViewModel()

        vm.checkOnStartup()
        advanceUntilIdle()

        val prompt = vm.state.value as StreakRescueUiState.Prompt
        assertEquals(StreakRescueOffer(streakDays = 5, missedDays = 3), prompt.offer)
        assertFalse(prompt.adFailed)
        assertFalse(prompt.isLoadingAd)
        assertEquals(now / day, promptDayStore.readLastPromptDay())
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
}
