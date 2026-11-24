package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.CategoryDecay
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.android.RewardedGate
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.mockk
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

/**
 * The four Brain RPG rewarded buttons through the real [RewardedGate] and the real Room repository, with the SDK replaced by a function:
 * - a VIP gets the reward written to the database and no ad is shown (the SDK would refuse a VIP and the button used to answer "ad not available"),
 * - a user whose ad is refused (cap shared with interstitial, no fill, closed early) gets nothing and is told `false`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainRpgRewardedGateTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private lateinit var viewModel: BrainRpgViewModel
    private val activity = mockk<Activity>(relaxed = true)

    private class Sdk(val answer: Boolean) {
        var shown = 0
        val show: (Activity, (Boolean) -> Unit) -> Unit = { _, done -> shown++; done(answer) }
    }

    private fun gate(vip: Boolean, sdk: Sdk) = RewardedGate(isVip = { vip }, show = sdk.show, preload = {})

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        repository = BrainRpgRepository(context, database, dao, importer, CoroutineScope(testDispatcher), testDispatcher)
        viewModel = BrainRpgViewModel(context, repository, QuizGeneratorService(), mockk(relaxed = true), QuizCache(), mockk(relaxed = true), testDispatcher, testDispatcher)
    }

    @After fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun shieldActive(): Boolean = requireNotNull(dao.getProfile()).streakShieldActive

    @Test fun streakShield_vip_isActivatedWithoutAnAd() = runTest(testDispatcher) {
        viewModel.onArticleReadFinished("a1", QuizGeneratorService.CATEGORY_TECH)
        advanceUntilIdle()
        assertFalse(shieldActive())
        val sdk = Sdk(answer = false) // even an SDK that would refuse
        var result: Boolean? = null
        viewModel.activateStreakShield(activity, gate(vip = true, sdk)) { result = it }
        advanceUntilIdle()
        assertEquals(true, result)
        assertTrue("the shield is really stored", shieldActive())
        assertEquals("no ad for a VIP", 0, sdk.shown)
    }

    @Test fun streakShield_refusedAd_changesNothing() = runTest(testDispatcher) {
        viewModel.onArticleReadFinished("a1", QuizGeneratorService.CATEGORY_TECH)
        advanceUntilIdle()
        val sdk = Sdk(answer = false)
        var result: Boolean? = null
        viewModel.activateStreakShield(activity, gate(vip = false, sdk)) { result = it }
        advanceUntilIdle()
        assertEquals(false, result)
        assertFalse("no shield without the reward", shieldActive())
        assertEquals(1, sdk.shown)
    }

    @Test fun streakShield_earnedAd_activatesTheShield() = runTest(testDispatcher) {
        viewModel.onArticleReadFinished("a1", QuizGeneratorService.CATEGORY_TECH)
        advanceUntilIdle()
        val sdk = Sdk(answer = true)
        var result: Boolean? = null
        viewModel.activateStreakShield(activity, gate(vip = false, sdk)) { result = it }
        advanceUntilIdle()
        assertEquals(true, result)
        assertTrue(shieldActive())
        assertEquals(1, sdk.shown)
    }

    @Test fun doubleXp_vip_getsTheBonusWithoutAnAd_andOnlyOnce() = runTest(testDispatcher) {
        // The bonus doubles a quiz reward, so the quiz must have been answered first (the repository refuses a bonus without it).
        viewModel.submitQuizAnswer("art-d", QuizGeneratorService.CATEGORY_TECH, true) { _, _ -> }
        advanceUntilIdle()
        val before = viewModel.userProgress.value.totalXp
        val sdk = Sdk(answer = false)
        val results = mutableListOf<Boolean>()
        viewModel.doubleQuizReward(activity, "art-d", QuizGeneratorService.CATEGORY_TECH, 150L, gate(vip = true, sdk)) { results += it }
        advanceUntilIdle()
        assertEquals(listOf(true), results)
        assertEquals(before + 150L, viewModel.userProgress.value.totalXp)
        assertEquals(0, sdk.shown)
        // the same article again: no second bonus, no ad
        viewModel.doubleQuizReward(activity, "art-d", QuizGeneratorService.CATEGORY_TECH, 150L, gate(vip = true, sdk)) { results += it }
        advanceUntilIdle()
        assertEquals(listOf(true, false), results)
        assertEquals("no second bonus", before + 150L, viewModel.userProgress.value.totalXp)
    }

    @Test fun doubleXp_refusedAd_awardsNothing() = runTest(testDispatcher) {
        val sdk = Sdk(answer = false)
        var result: Boolean? = null
        viewModel.submitQuizAnswer("art-e", QuizGeneratorService.CATEGORY_TECH, true) { _, _ -> }
        advanceUntilIdle()
        val before = viewModel.userProgress.value.totalXp
        viewModel.doubleQuizReward(activity, "art-e", QuizGeneratorService.CATEGORY_TECH, 150L, gate(vip = false, sdk)) { result = it }
        advanceUntilIdle()
        assertEquals(false, result)
        assertEquals("no bonus without the reward", before, viewModel.userProgress.value.totalXp)
        assertEquals(1, sdk.shown)
    }

    @Test fun retryQuiz_vipIsAllowedWithoutAnAd_refusedAdIsNot() = runTest(testDispatcher) {
        val a = Sdk(answer = false); val b = Sdk(answer = false)
        var vip: Boolean? = null; var free: Boolean? = null
        viewModel.retryQuiz(activity, gate(vip = true, a)) { vip = it }
        viewModel.retryQuiz(activity, gate(vip = false, b)) { free = it }
        assertEquals(true, vip); assertEquals(0, a.shown)
        assertEquals(false, free); assertEquals(1, b.shown)
    }

    @Test fun recoverCategory_vip_resetsTheDecayWithoutAnAd() = runTest(testDispatcher) {
        viewModel.onArticleReadFinished("a-t", QuizGeneratorService.CATEGORY_TECH)
        advanceUntilIdle()
        val old = System.currentTimeMillis() - 3 * CategoryDecay.WEEK_MILLIS
        dao.upsertCategory(requireNotNull(dao.getCategory(QuizGeneratorService.CATEGORY_TECH)).copy(lastPracticedAtMillis = old))
        viewModel.refresh()
        advanceUntilIdle()
        val sdk = Sdk(answer = false)
        var result: Boolean? = null
        viewModel.recoverCategory(activity, QuizGeneratorService.CATEGORY_TECH, gate(vip = true, sdk)) { result = it }
        advanceUntilIdle()
        assertEquals(true, result)
        assertTrue(requireNotNull(dao.getCategory(QuizGeneratorService.CATEGORY_TECH)).lastPracticedAtMillis > old)
        assertEquals(0, sdk.shown)
    }

    @Test fun recoverCategory_refusedAd_leavesTheDecay() = runTest(testDispatcher) {
        viewModel.onArticleReadFinished("a-t", QuizGeneratorService.CATEGORY_TECH)
        advanceUntilIdle()
        val old = System.currentTimeMillis() - 3 * CategoryDecay.WEEK_MILLIS
        dao.upsertCategory(requireNotNull(dao.getCategory(QuizGeneratorService.CATEGORY_TECH)).copy(lastPracticedAtMillis = old))
        viewModel.refresh()
        advanceUntilIdle()
        val sdk = Sdk(answer = false)
        var result: Boolean? = null
        viewModel.recoverCategory(activity, QuizGeneratorService.CATEGORY_TECH, gate(vip = false, sdk)) { result = it }
        advanceUntilIdle()
        assertEquals(false, result)
        assertEquals(old, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
    }
}
