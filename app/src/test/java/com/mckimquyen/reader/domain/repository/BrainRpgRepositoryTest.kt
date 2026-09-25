package com.mckimquyen.reader.domain.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainRpgRepositoryTest {

    // Unconfined: the async initial load runs inline, so tests see persisted state right away.
    private val eagerScope = CoroutineScope(Dispatchers.Unconfined)

    private lateinit var context: Context
    private lateinit var repository: BrainRpgRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs for clean test isolation
        context.getSharedPreferences("brain_rpg_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = BrainRpgRepository(context, eagerScope, Dispatchers.Unconfined)
    }

    @Test
    fun getProgress_initialState_returnsDefaultProgress() {
        val progress = repository.getProgress()
        assertEquals(0L, progress.totalXp)
        assertEquals(1, progress.level)
        assertEquals("Novice Scholar", progress.levelTitle)
        assertEquals(1, progress.streakDays)
        assertFalse(progress.streakShieldActive)
        assertEquals(0, progress.quizzesAttempted)
        assertEquals(0, progress.quizzesPassed)
    }

    @Test
    fun addReadingXp_incrementsTotalXpAndCategory() {
        val updated = repository.addReadingXp(QuizGeneratorService.CATEGORY_TECH, 50L)

        assertEquals(50L, updated.totalXp)
        assertEquals(50L, updated.categoryXp[QuizGeneratorService.CATEGORY_TECH])
        assertEquals(1, updated.level)
    }

    @Test
    fun addReadingXp_advancesLevelWhenPassingThreshold() {
        val updated = repository.addReadingXp(QuizGeneratorService.CATEGORY_SCIENCE, 150L)

        assertEquals(150L, updated.totalXp)
        assertEquals(2, updated.level)
        assertEquals("Curious Reader", updated.levelTitle)
        assertEquals(100L, updated.currentLevelBaseXp)
        assertEquals(300L, updated.nextLevelTargetXp)
    }

    @Test
    fun submitQuizResult_correct_awards150Xp() {
        val (updated, awarded) = repository.submitQuizResult(QuizGeneratorService.CATEGORY_TECH, true)

        assertEquals(150L, awarded)
        assertEquals(150L, updated.totalXp)
        assertEquals(1, updated.quizzesAttempted)
        assertEquals(1, updated.quizzesPassed)
        assertEquals(100, updated.quizAccuracyPercent)
    }

    @Test
    fun submitQuizResult_incorrect_awards0Xp() {
        val (updated, awarded) = repository.submitQuizResult(QuizGeneratorService.CATEGORY_BUSINESS, false)

        assertEquals(0L, awarded)
        assertEquals(0L, updated.totalXp)
        assertEquals(1, updated.quizzesAttempted)
        assertEquals(0, updated.quizzesPassed)
        assertEquals(0, updated.quizAccuracyPercent)
    }

    @Test
    fun activateStreakShield_activatesShieldState() {
        assertFalse(repository.getProgress().streakShieldActive)
        val success = repository.activateStreakShield()
        assertTrue(success)
        assertTrue(repository.getProgress().streakShieldActive)
    }

    @Test
    fun awardDoubleXpForQuiz_addsBonusXp() {
        repository.submitQuizResult(QuizGeneratorService.CATEGORY_TECH, true)
        val doubled = repository.awardDoubleXpForQuiz(QuizGeneratorService.CATEGORY_TECH, 150L)

        assertEquals(300L, doubled.totalXp)
        assertEquals(300L, doubled.categoryXp[QuizGeneratorService.CATEGORY_TECH])
    }

    @Test
    fun constructor_loadsPersistedProgressAsynchronously() {
        repository.addReadingXp(QuizGeneratorService.CATEGORY_TECH, 150L)
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)

        val lazyRepository = BrainRpgRepository(context, scope, dispatcher)

        // Nothing loaded in the constructor: the flow still holds the in-memory default.
        assertEquals(0L, lazyRepository.userProgress.value.totalXp)

        scope.testScheduler.advanceUntilIdle()

        assertEquals(150L, lazyRepository.userProgress.value.totalXp)
        assertEquals(2, lazyRepository.userProgress.value.level)
    }

    @Test
    fun addReadingXp_beforeAsyncLoad_addsToPersistedTotal() {
        repository.addReadingXp(QuizGeneratorService.CATEGORY_TECH, 150L)
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val lazyRepository = BrainRpgRepository(context, scope, dispatcher)

        // Awarding XP before the async load must not reset the saved 150 XP to 0 + 50.
        val updated = lazyRepository.addReadingXp(QuizGeneratorService.CATEGORY_TECH, 50L)
        scope.testScheduler.advanceUntilIdle()

        assertEquals(200L, updated.totalXp)
        assertEquals(200L, lazyRepository.userProgress.value.totalXp)
        assertEquals(200L, lazyRepository.userProgress.value.categoryXp[QuizGeneratorService.CATEGORY_TECH])
    }
}
