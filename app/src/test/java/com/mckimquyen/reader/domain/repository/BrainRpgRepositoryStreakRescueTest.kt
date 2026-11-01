package com.mckimquyen.reader.domain.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.StreakRescuePolicy
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
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
class BrainRpgRepositoryStreakRescueTest {
    private val day = StreakRescuePolicy.MILLIS_PER_DAY
    private val now = 20_000L * day
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        repository = BrainRpgRepository(context, database, dao, importer, newScope(), Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        database.close()
    }

    private suspend fun seed(streak: Int, lastReadDaysAgo: Long, shield: Boolean = false) {
        dao.upsertProfile(
            BrainRpgProfileEntity(
                id = 1,
                streakDays = streak,
                lastReadEpochDay = (now / day) - lastReadDaysAgo,
                streakShieldActive = shield,
                legacyImportComplete = true,
            ),
        )
        repository.refresh(now)
    }

    @Test fun resetBrokenStreak_setsStreakDaysToZero_andNextReadStartsAtOne() = runTest {
        seed(streak = 5, lastReadDaysAgo = 3)

        assertTrue(repository.resetBrokenStreak(now))
        assertEquals(0, repository.getProgress().streakDays)

        repository.awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = now)
        assertEquals(1, repository.getProgress().streakDays)
    }

    @Test fun resetBrokenStreak_rejectsWhenStreakIsAlive() = runTest {
        seed(streak = 5, lastReadDaysAgo = 1)

        assertFalse(repository.resetBrokenStreak(now))
        assertEquals(5, repository.getProgress().streakDays)
    }

    @Test fun resetBrokenStreak_rejectsWhenShieldIsArmed() = runTest {
        seed(streak = 5, lastReadDaysAgo = 4, shield = true)

        assertFalse(repository.resetBrokenStreak(now))
        assertEquals(5, repository.getProgress().streakDays)
    }

    @Test fun rescueBrokenStreak_armsShield_andNextReadIncrementsStreak() = runTest {
        seed(streak = 5, lastReadDaysAgo = 3)

        assertTrue(repository.rescueBrokenStreak(now))
        assertTrue(repository.getProgress().streakShieldActive)
        assertEquals(5, repository.getProgress().streakDays)

        repository.awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = now)
        assertEquals(6, repository.getProgress().streakDays)
        assertFalse(repository.getProgress().streakShieldActive)
    }

    @Test fun rescueBrokenStreak_isIdempotent_secondCallReturnsFalse() = runTest {
        seed(streak = 5, lastReadDaysAgo = 3)

        assertTrue(repository.rescueBrokenStreak(now))
        assertFalse("second call with the shield already armed must not report a change", repository.rescueBrokenStreak(now))
    }

    @Test fun rescueBrokenStreak_rejectsWhenStreakIsStillAlive() = runTest {
        seed(streak = 5, lastReadDaysAgo = 1)

        assertFalse(repository.rescueBrokenStreak(now))
        assertFalse(repository.getProgress().streakShieldActive)
    }

    @Test fun bothMutations_rejectWhenNothingWasEverRead() = runTest {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 5, lastReadEpochDay = 0L, legacyImportComplete = true))
        repository.refresh(now)

        assertFalse(repository.resetBrokenStreak(now))
        assertFalse(repository.rescueBrokenStreak(now))
    }

    @Test fun bothMutations_rejectWhenSavedStreakIsBelowTheOfferMinimum() = runTest {
        // A late ad callback must not arm a shield (or wipe a streak) the policy would never have offered.
        seed(streak = StreakRescuePolicy.MIN_STREAK_TO_OFFER - 1, lastReadDaysAgo = 5)

        assertFalse(repository.rescueBrokenStreak(now))
        assertFalse(repository.getProgress().streakShieldActive)
        assertFalse(repository.resetBrokenStreak(now))
        assertEquals(StreakRescuePolicy.MIN_STREAK_TO_OFFER - 1, repository.getProgress().streakDays)
    }

    @Test fun bothMutations_acceptAStreakExactlyAtTheOfferMinimum() = runTest {
        seed(streak = StreakRescuePolicy.MIN_STREAK_TO_OFFER, lastReadDaysAgo = 2)

        assertTrue(repository.rescueBrokenStreak(now))
    }
}
