package com.mckimquyen.reader.ui.page.rpg

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class BrainRpgViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private lateinit var quizGenerator: QuizGeneratorService
    private lateinit var viewModel: BrainRpgViewModel

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
        repository = BrainRpgRepository(context, database, dao, importer, CoroutineScope(testDispatcher), testDispatcher)
        quizGenerator = QuizGeneratorService()
        viewModel = BrainRpgViewModel(repository, quizGenerator)
    }

    @After fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test fun onArticleReadFinished_deduplicatesSameArticleId_acrossRepositoryAndViewModelLifetime() = runTest(testDispatcher) {
        var rewarded = 0
        viewModel.onArticleReadFinished("art_1", QuizGeneratorService.CATEGORY_TECH) { rewarded++ }
        advanceUntilIdle()
        assertEquals(50L, viewModel.userProgress.value.totalXp)
        assertEquals(1, rewarded)

        val recreated = BrainRpgViewModel(repository, quizGenerator)
        recreated.onArticleReadFinished("art_1", QuizGeneratorService.CATEGORY_TECH) { rewarded++ }
        advanceUntilIdle()
        recreated.onArticleReadFinished("art_2", QuizGeneratorService.CATEGORY_TECH) { rewarded++ }
        advanceUntilIdle()

        assertEquals(100L, recreated.userProgress.value.totalXp)
        assertEquals(2, rewarded)
    }

    @Test fun submitQuizAnswer_correctUpdatesProgressAndDuplicateReturnsZero() = runTest(testDispatcher) {
        val awards = mutableListOf<Long>()
        viewModel.submitQuizAnswer("art_q", QuizGeneratorService.CATEGORY_TECH, true) { _, xp -> awards += xp }
        advanceUntilIdle()
        viewModel.submitQuizAnswer("art_q", QuizGeneratorService.CATEGORY_TECH, true) { _, xp -> awards += xp }
        advanceUntilIdle()

        assertEquals(listOf(150L, 0L), awards)
        assertEquals(150L, viewModel.userProgress.value.totalXp)
        assertEquals(1, viewModel.userProgress.value.quizzesPassed)
    }

    @Test fun wrongQuizAnswerCountsAttempt_butDoesNotAwardOrPractice() = runTest(testDispatcher) {
        viewModel.submitQuizAnswer("art_q", QuizGeneratorService.CATEGORY_TECH, false) { _, xp -> assertEquals(0L, xp) }
        advanceUntilIdle()
        assertEquals(1, viewModel.userProgress.value.quizzesAttempted)
        assertEquals(0, viewModel.userProgress.value.quizzesPassed)
        assertEquals(0L, viewModel.userProgress.value.totalXp)
        assertEquals(null, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
    }

    @Test fun duplicateCorrectQuizStillRefreshesPracticeButNotXPOrCounters() = runTest(testDispatcher) {
        viewModel.submitQuizAnswer("art_q", QuizGeneratorService.CATEGORY_TECH, true) { _, _ -> }
        advanceUntilIdle()
        val timeBefore = dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis
        viewModel.submitQuizAnswer("art_q", QuizGeneratorService.CATEGORY_TECH, true) { _, xp -> assertEquals(0L, xp) }
        advanceUntilIdle()

        assertTrue((dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis ?: 0L) >= (timeBefore ?: 0L))
        assertEquals(1, viewModel.userProgress.value.quizzesAttempted)
        assertEquals(1, viewModel.userProgress.value.quizzesPassed)
    }

    @Test fun doubleRewardClaimedIds_areExposedAsFlowForCompose_notSyncRoomReads() = runTest(testDispatcher) {
        viewModel.submitQuizAnswer("art_d", QuizGeneratorService.CATEGORY_TECH, true) { _, _ -> }
        advanceUntilIdle()
        viewModel.claimDoubleReward("art_d", QuizGeneratorService.CATEGORY_TECH, 150L) { assertTrue(it) }
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.doubleRewardedArticleIds.value.contains("art_d"))
    }
}
