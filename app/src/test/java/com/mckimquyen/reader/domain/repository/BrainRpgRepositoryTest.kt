package com.mckimquyen.reader.domain.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
class BrainRpgRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var importer: BrainRpgLegacyImporter
    private lateinit var repository: BrainRpgRepository
    private val scopes = mutableListOf<CoroutineScope>()

    // Each repository launches a startup refresh; cancel it before the DB closes so it cannot outlive the test.
    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        dao = database.brainRpgDao()
        importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        repository = BrainRpgRepository(context, database, dao, importer, newScope(), Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        database.close()
    }

    private fun newRepository() = BrainRpgRepository(
        context,
        database,
        dao,
        BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao),
        newScope(),
        Dispatchers.Unconfined,
    )

    @Test fun firstProgressRead_importsLegacyPrefsBeforeExposingRoomState() = runTest {
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 640L)
            .putLong(BrainRpgLegacyPreferences.categoryKey(QuizGeneratorService.CATEGORY_TECH), 320L)
            .putInt(BrainRpgLegacyPreferences.KEY_STREAK_DAYS, 5)
            .commit()
        val loaded = newRepository().refresh()
        assertEquals(640L, loaded.totalXp)
        assertEquals(320L, loaded.categoryXp[QuizGeneratorService.CATEGORY_TECH])
        assertEquals(5, loaded.streakDays)
        assertTrue(dao.getProfile()?.legacyImportComplete == true)
    }

    @Test fun initialProgress_usesExistingDefaults() = runTest {
        val progress = repository.refresh()
        assertEquals(0L, progress.totalXp)
        assertEquals(1, progress.level)
        assertEquals("Novice Scholar", progress.levelTitle)
        assertEquals(1, progress.streakDays)
        assertFalse(progress.streakShieldActive)
        assertEquals(0, progress.quizzesAttempted)
        assertEquals(0, progress.quizzesPassed)
    }

    @Test fun readingAward_updatesProfileAndCategoryBaseXp() = runTest {
        val updated = repository.awardReadingXpOnce("r-tech", QuizGeneratorService.CATEGORY_TECH, 50L, NOW).let { repository.refresh() }
        assertEquals(50L, updated.totalXp)
        assertEquals(50L, updated.categoryXp[QuizGeneratorService.CATEGORY_TECH])
        assertEquals(1, updated.level)
        assertEquals(NOW, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
        val event = dao.allXpEvents().single()
        assertEquals(QuizGeneratorService.CATEGORY_TECH, event.categoryId)
        assertEquals(50L, event.xpDelta)
        assertEquals(BrainRpgRewardType.READ, event.rewardType)
        assertEquals(NOW, event.createdAtMillis)
    }

    @Test fun readingAward_crossingThreshold_updatesDerivedLevel() = runTest {
        val updated = repository.awardReadingXpOnce("r-sci", QuizGeneratorService.CATEGORY_SCIENCE, 150L, NOW).let { repository.refresh() }
        assertEquals(150L, updated.totalXp)
        assertEquals(2, updated.level)
        assertEquals("Curious Reader", updated.levelTitle)
        assertEquals(100L, updated.currentLevelBaseXp)
        assertEquals(300L, updated.nextLevelTargetXp)
    }

    @Test fun readingReward_duplicateAfterRestart_returnsZeroAndPersistsOnce() = runTest {
        assertEquals(50L, repository.awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW))
        assertEquals(0L, newRepository().awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW + 1))
        assertEquals(50L, dao.getProfile()?.baseTotalXp)
        assertTrue(dao.hasReward("a1", BrainRpgRewardType.READ))
        assertEquals(1, dao.allXpEvents().size)
        assertEquals(1, dao.allXpEvents().size)
    }

    @Test fun categoryXpDecaysForDisplayButBaseXpAndTotalXpStayUnchanged() = runTest {
        repository.awardReadingXpOnce("decay", QuizGeneratorService.CATEGORY_TECH, 100L, NOW)
        val decayed = repository.refresh(nowMillis = NOW + com.mckimquyen.reader.domain.model.rpg.CategoryDecay.WEEK_MILLIS)
        assertEquals(90L, decayed.categoryXp[QuizGeneratorService.CATEGORY_TECH])
        assertEquals(100L, decayed.categoryBaseXp[QuizGeneratorService.CATEGORY_TECH])
        assertEquals(100L, decayed.totalXp)
        assertEquals(100L, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.baseXp)
    }

    @Test fun recoverCategory_resetsOnlyThatCategoryPracticeTime_withoutChangingXp() = runTest {
        repository.awardReadingXpOnce("r-tech", QuizGeneratorService.CATEGORY_TECH, 100L, NOW)
        repository.awardReadingXpOnce("r-sci", QuizGeneratorService.CATEGORY_SCIENCE, 80L, NOW)
        val later = NOW + 3 * com.mckimquyen.reader.domain.model.rpg.CategoryDecay.WEEK_MILLIS

        assertTrue(repository.recoverCategory(QuizGeneratorService.CATEGORY_TECH, later))

        assertEquals(later, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
        assertEquals(NOW, dao.getCategory(QuizGeneratorService.CATEGORY_SCIENCE)?.lastPracticedAtMillis)
        assertEquals(100L, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.baseXp)
        assertEquals(180L, dao.getProfile()?.baseTotalXp)
        val progress = repository.refresh(later)
        assertEquals(100L, progress.categoryXp[QuizGeneratorService.CATEGORY_TECH])
        assertTrue(progress.categoryXp[QuizGeneratorService.CATEGORY_SCIENCE]!! < 80L)
    }

    @Test fun recoverCategory_refusesWhenNotDecayed_unknownOrBlank() = runTest {
        repository.awardReadingXpOnce("r-tech", QuizGeneratorService.CATEGORY_TECH, 100L, NOW)
        assertFalse("fresh category has nothing to recover", repository.recoverCategory(QuizGeneratorService.CATEGORY_TECH, NOW + 1_000L))
        assertFalse(repository.recoverCategory("never-practiced", NOW + 10 * com.mckimquyen.reader.domain.model.rpg.CategoryDecay.WEEK_MILLIS))
        assertFalse(repository.recoverCategory("  ", NOW + 10 * com.mckimquyen.reader.domain.model.rpg.CategoryDecay.WEEK_MILLIS))
        assertEquals(NOW, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
    }

    @Test fun recoverCategory_clockRollback_neverMovesPracticeTimeBackwards() = runTest {
        repository.awardReadingXpOnce("r-tech", QuizGeneratorService.CATEGORY_TECH, 100L, NOW)
        assertFalse(repository.recoverCategory(QuizGeneratorService.CATEGORY_TECH, NOW - 1_000L))
        assertEquals(NOW, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
    }

    @Test fun recoverCategory_secondCallRightAfterIsRefused() = runTest {
        repository.awardReadingXpOnce("r-tech", QuizGeneratorService.CATEGORY_TECH, 100L, NOW)
        val later = NOW + 2 * com.mckimquyen.reader.domain.model.rpg.CategoryDecay.WEEK_MILLIS
        assertTrue(repository.recoverCategory(QuizGeneratorService.CATEGORY_TECH, later))
        assertFalse("a duplicate ad callback must not recover twice", repository.recoverCategory(QuizGeneratorService.CATEGORY_TECH, later + 1_000L))
    }

    @Test fun repeatedQualifyingRead_doesNotAwardAgain_butRefreshesPractice() = runTest {
        repository.awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW)
        assertEquals(0L, repository.awardReadingXpOnce("a1", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW + 1_000))
        assertEquals(NOW + 1_000, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
        assertEquals(50L, dao.getProfile()?.baseTotalXp)
    }

    @Test fun correctQuiz_awardsOnce_andDuplicateDoesNotChangeCounters() = runTest {
        val (first, firstXp) = repository.submitQuizResultOnce("q1", QuizGeneratorService.CATEGORY_TECH, true, NOW)
        val (again, againXp) = newRepository().submitQuizResultOnce("q1", QuizGeneratorService.CATEGORY_TECH, true, NOW + 100)
        assertEquals(150L, firstXp)
        assertEquals(0L, againXp)
        assertEquals(150L, first.totalXp)
        assertEquals(150L, again.totalXp)
        assertEquals(1, again.quizzesAttempted)
        assertEquals(1, again.quizzesPassed)
        assertEquals(NOW + 100, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
    }

    @Test fun wrongQuiz_countsAttemptButDoesNotConsumeRewardOrPractice() = runTest {
        val (_, wrongXp) = repository.submitQuizResultOnce("q1", QuizGeneratorService.CATEGORY_TECH, false, NOW)
        assertEquals(0L, wrongXp)
        assertFalse(dao.hasReward("q1", BrainRpgRewardType.QUIZ))
        assertEquals(null, dao.getCategory(QuizGeneratorService.CATEGORY_TECH)?.lastPracticedAtMillis)
        val (retry, retryXp) = repository.submitQuizResultOnce("q1", QuizGeneratorService.CATEGORY_TECH, true, NOW + 1)
        assertEquals(150L, retryXp)
        assertEquals(2, retry.quizzesAttempted)
        assertEquals(1, retry.quizzesPassed)
    }

    @Test fun concurrentDuplicateReads_awardOnce() = runTest {
        val results = listOf(
            async(Dispatchers.Default) { repository.awardReadingXpOnce("parallel", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW) },
            async(Dispatchers.Default) { repository.awardReadingXpOnce("parallel", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW) },
        ).map { it.await() }
        assertEquals(listOf(0L, 50L), results.sorted())
        assertEquals(50L, dao.getProfile()?.baseTotalXp)
    }

    @Test fun blankArticleId_neverAwards() = runTest {
        assertEquals(0L, repository.awardReadingXpOnce("", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW))
        assertEquals(0L, repository.awardReadingXpOnce("   ", QuizGeneratorService.CATEGORY_TECH, nowMillis = NOW))
        assertEquals(0L, repository.refresh().totalXp)
        assertEquals(emptyList<Any>(), dao.allRewards())
    }

    @Test fun doubleReward_requiresCorrectQuiz_andIsOncePerArticleAcrossRestart() = runTest {
        assertEquals(0L, repository.awardDoubleXpForQuizOnce("q", QuizGeneratorService.CATEGORY_TECH, 150L))
        repository.submitQuizResultOnce("q", QuizGeneratorService.CATEGORY_TECH, true, NOW)
        assertEquals(150L, repository.awardDoubleXpForQuizOnce("q", QuizGeneratorService.CATEGORY_TECH, 150L))
        assertEquals(0L, newRepository().awardDoubleXpForQuizOnce("q", QuizGeneratorService.CATEGORY_TECH, 150L))
        assertEquals(300L, dao.getProfile()?.baseTotalXp)
    }

    @Test fun wrongQuizAttempt_updatesAttemptsOnly() = runTest {
        val progress = repository.recordWrongQuizAttempt()
        assertEquals(1, progress.quizzesAttempted)
        assertEquals(0, progress.quizzesPassed)
        assertEquals(0L, progress.totalXp)
    }

    // ---- RPG-06: streak counting and shield, driven by an explicit clock (nowMillis) ----

    private val day = 86_400_000L

    /** Each read is a distinct article so the once-per-article rule never hides the streak logic. */
    private suspend fun readAt(articleId: String, nowMillis: Long) =
        repository.awardReadingXpOnce(articleId, QuizGeneratorService.CATEGORY_TECH, nowMillis = nowMillis)

    private suspend fun streak() = dao.getProfile()!!.let { it.streakDays to it.streakShieldActive }

    @Test fun streak_firstEverRead_startsAtOne() = runTest {
        readAt("s1", NOW)
        assertEquals(1 to false, streak())
        assertEquals(NOW / day, dao.getProfile()!!.lastReadEpochDay)
    }

    @Test fun streak_readingOnConsecutiveDays_increasesByOnePerDay() = runTest {
        readAt("s1", NOW)
        readAt("s2", NOW + day)
        readAt("s3", NOW + 2 * day)
        assertEquals(3 to false, streak())
    }

    @Test fun streak_manyReadsOnTheSameDay_countOnce() = runTest {
        readAt("s1", NOW)
        readAt("s2", NOW + 1_000L)
        readAt("s3", NOW + 2_000L)
        assertEquals(1 to false, streak())
    }

    @Test fun streak_missingOneDay_withoutShield_resetsToOne() = runTest {
        readAt("s1", NOW)
        readAt("s2", NOW + day)
        readAt("s3", NOW + 3 * day)
        assertEquals(1 to false, streak())
    }

    @Test fun streak_missingManyDays_withoutShield_resetsToOne() = runTest {
        readAt("s1", NOW)
        readAt("s2", NOW + day)
        readAt("s3", NOW + 30 * day)
        assertEquals(1 to false, streak())
    }

    @Test fun streak_missingADay_withShield_continuesAndConsumesTheShield() = runTest {
        readAt("s1", NOW)
        readAt("s2", NOW + day)
        assertTrue(repository.activateStreakShield())
        assertEquals(2 to true, streak())

        readAt("s3", NOW + 3 * day)

        assertEquals(3 to false, streak())
    }

    @Test fun streak_afterShieldIsConsumed_nextGapResets() = runTest {
        readAt("s1", NOW)
        repository.activateStreakShield()
        readAt("s2", NOW + 3 * day)
        assertEquals(2 to false, streak())

        readAt("s3", NOW + 6 * day)

        assertEquals(1 to false, streak())
    }

    @Test fun streak_shieldStaysArmedWhileReadingEveryDay() = runTest {
        readAt("s1", NOW)
        repository.activateStreakShield()
        readAt("s2", NOW + day)
        readAt("s3", NOW + 2 * day)
        assertEquals(3 to true, streak())
    }

    /**
     * Documents current behaviour, not a requirement: a shield bridges a gap of ANY length, not just one day. Left as is on
     * purpose (owner chose "tests only"); change this test together with `nextStreak` if the rule is ever tightened.
     */
    @Test fun streak_shieldBridgesAGapOfAnyLength() = runTest {
        readAt("s1", NOW)
        repository.activateStreakShield()
        readAt("s2", NOW + 40 * day)
        assertEquals(2 to false, streak())
    }

    /** Current behaviour: a read dated before the stored last-read day (clock moved back) resets the streak. */
    @Test fun streak_clockMovedBackwards_resetsToOne() = runTest {
        readAt("s1", NOW)
        readAt("s2", NOW + day)
        readAt("s3", NOW - 5 * day)
        assertEquals(1 to false, streak())
    }

    /** Current behaviour: the day boundary is UTC midnight, independent of the device time zone. */
    @Test fun streak_dayBoundaryIsUtcMidnight() = runTest {
        val justBeforeMidnightUtc = (NOW / day) * day + day - 1
        readAt("s1", justBeforeMidnightUtc)
        readAt("s2", justBeforeMidnightUtc + 2)
        assertEquals(2 to false, streak())
    }

    /** Current behaviour: only reading (and triage) touch the streak; a correct quiz answer does not. */
    @Test fun streak_quizAnswerDoesNotAdvanceTheStreak() = runTest {
        readAt("s1", NOW)
        repository.submitQuizResultOnce("q1", QuizGeneratorService.CATEGORY_TECH, true, nowMillis = NOW + day)
        assertEquals(1 to false, streak())
        assertEquals(NOW / day, dao.getProfile()!!.lastReadEpochDay)
    }

    /**
     * Current behaviour: the streak counts days on which a qualifying read happened, even when the article was already
     * rewarded (XP is awarded once, the streak is not). So re-reading an old article on the next day keeps the streak alive.
     */
    @Test fun streak_rereadingAnAlreadyRewardedArticleOnALaterDay_stillCountsTheDay() = runTest {
        assertEquals(50L, readAt("s1", NOW))
        assertEquals(0L, readAt("s1", NOW + day))
        assertEquals(2 to false, streak())
        assertEquals(50L, dao.getProfile()!!.baseTotalXp)
    }

    @Test fun streak_triageAdvancesTheStreakLikeReading() = runTest {
        readAt("s1", NOW)
        repository.awardTriageXpOnce("t1", nowMillis = NOW + day)
        assertEquals(2 to false, streak())
    }

    private companion object { const val NOW = 1_800_000_000_000L }
}
