package com.mckimquyen.reader.ui.page.rpg

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainRpgViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: BrainRpgRepository
    private lateinit var quizGenerator: QuizGeneratorService
    private lateinit var viewModel: BrainRpgViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("brain_rpg_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = BrainRpgRepository(context)
        quizGenerator = QuizGeneratorService()
        viewModel = BrainRpgViewModel(repository, quizGenerator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun userProgress_initialState_emitsValidDefaults() {
        val initial = viewModel.userProgress.value
        assertEquals(0L, initial.totalXp)
        assertEquals(1, initial.level)
    }

    @Test
    fun onArticleReadFinished_deduplicatesSameArticleId() = runTest(testDispatcher) {
        viewModel.onArticleReadFinished("art_1", QuizGeneratorService.CATEGORY_TECH)
        testScheduler.advanceUntilIdle()

        val progress1 = viewModel.userProgress.value
        assertEquals(50L, progress1.totalXp)

        // Reading same article again should NOT award XP again
        viewModel.onArticleReadFinished("art_1", QuizGeneratorService.CATEGORY_TECH)
        testScheduler.advanceUntilIdle()

        val progress2 = viewModel.userProgress.value
        assertEquals(50L, progress2.totalXp)

        // Reading different article DOES award XP
        viewModel.onArticleReadFinished("art_2", QuizGeneratorService.CATEGORY_TECH)
        testScheduler.advanceUntilIdle()

        val progress3 = viewModel.userProgress.value
        assertEquals(100L, progress3.totalXp)
    }

    @Test
    fun submitQuizAnswer_correct_updatesProgressWithAward() = runTest(testDispatcher) {
        var callbackAwarded = -1L
        viewModel.submitQuizAnswer(QuizGeneratorService.CATEGORY_TECH, true) { _, xp ->
            callbackAwarded = xp
        }
        testScheduler.advanceUntilIdle()

        assertEquals(150L, callbackAwarded)
        val progress = viewModel.userProgress.value
        assertEquals(150L, progress.totalXp)
        assertEquals(1, progress.quizzesPassed)
    }

    @Test
    fun onArticleReadFinished_callsOnRewardedOnlyOnFirstRead() = runTest(testDispatcher) {
        var rewardInvoked = 0
        viewModel.onArticleReadFinished("art_cb", QuizGeneratorService.CATEGORY_TECH) {
            rewardInvoked++
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, rewardInvoked)

        // Second time on same article
        viewModel.onArticleReadFinished("art_cb", QuizGeneratorService.CATEGORY_TECH) {
            rewardInvoked++
        }
        testScheduler.advanceUntilIdle()
        // Should STILL be 1 because it was deduplicated
        assertEquals(1, rewardInvoked)
    }
}
