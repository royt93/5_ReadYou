package com.mckimquyen.reader.ui.page.rpg

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real file-backed Room database: "restart" closes it and reopens the same file with a fresh repository,
 * which is the closest on-device equivalent of a process restart.
 */
@RunWith(AndroidJUnit4::class)
class BrainRpgIntegrationTest {

    private lateinit var context: Context
    private lateinit var dbName: String
    private val databases = mutableListOf<AndroidDatabase>()
    private val scopes = mutableListOf<CoroutineScope>()
    private lateinit var repository: BrainRpgRepository

    private val tech = QuizGeneratorService.CATEGORY_TECH

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "brain_rpg_it_${System.nanoTime()}.db"
        legacyPrefs().edit().clear().commit()
        repository = openRepository()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        databases.forEach { if (it.isOpen) it.close() }
        context.deleteDatabase(dbName)
        legacyPrefs().edit().clear().commit()
    }

    private fun legacyPrefs() = context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    private fun openRepository(): BrainRpgRepository {
        val db = Room.databaseBuilder(context, AndroidDatabase::class.java, dbName).build().also { databases += it }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val dao = db.brainRpgDao()
        return BrainRpgRepository(context, db, dao, BrainRpgLegacyImporter(db, legacyPrefs(), dao), scope, Dispatchers.IO)
    }

    private fun restartedRepository(): BrainRpgRepository {
        scopes.forEach { it.cancel() }
        databases.forEach { if (it.isOpen) it.close() }
        return openRepository()
    }

    @Test
    fun fullRpgProgressionFlow_integration() = runBlocking {
        var current = repository.refresh()
        assertEquals(0L, current.totalXp)
        assertEquals(1, current.level)
        assertEquals("Novice Scholar", current.levelTitle)
        assertEquals(1, current.streakDays)
        assertFalse(current.streakShieldActive)

        assertEquals(50L, repository.awardReadingXpOnce("flow-art", tech, 50L))
        current = repository.userProgress.value
        assertEquals(50L, current.totalXp)
        assertEquals(1, current.level)
        assertEquals(50L, current.categoryXp[tech])

        repository.submitQuizResultOnce("flow-art", tech, true)
        current = repository.userProgress.value
        assertEquals(200L, current.totalXp)
        assertEquals(2, current.level)
        assertEquals("Curious Reader", current.levelTitle)
        assertEquals(1, current.quizzesAttempted)
        assertEquals(1, current.quizzesPassed)
        assertEquals(100, current.quizAccuracyPercent)

        assertEquals(150L, repository.awardDoubleXpForQuizOnce("flow-art", tech, 150L))
        current = repository.userProgress.value
        assertEquals(350L, current.totalXp)
        assertEquals(3, current.level)
        assertEquals("Knowledge Seeker", current.levelTitle)

        assertTrue(repository.activateStreakShield())
        assertTrue(repository.userProgress.value.streakShieldActive)

        val persisted = restartedRepository().refresh()
        assertEquals(350L, persisted.totalXp)
        assertEquals(3, persisted.level)
        assertEquals("Knowledge Seeker", persisted.levelTitle)
        assertTrue(persisted.streakShieldActive)
        assertEquals(100, persisted.quizAccuracyPercent)
        assertEquals(350L, persisted.categoryXp[tech])
    }

    @Test
    fun readingXp_isAwardedOncePerArticle_evenAfterTheProcessRestarts() = runBlocking {
        assertEquals(50L, repository.awardReadingXpOnce("art-int-1", tech, 50L))

        val afterRestart = restartedRepository()
        assertEquals(50L, afterRestart.refresh().totalXp)
        assertEquals(0L, afterRestart.awardReadingXpOnce("art-int-1", tech, 50L))
        assertEquals("XP is unchanged by a repeat", 50L, afterRestart.userProgress.value.totalXp)
        assertEquals(50L, afterRestart.awardReadingXpOnce("art-int-2", tech, 50L))
    }

    @Test
    fun repeatRead_refreshesPracticeTimeButNeverXp_acrossRestart() = runBlocking {
        val first = 1_800_000_000_000L
        val later = first + 7 * 86_400_000L
        repository.awardReadingXpOnce("art-int-p", tech, 50L, first)

        val afterRestart = restartedRepository()
        assertEquals(0L, afterRestart.awardReadingXpOnce("art-int-p", tech, 50L, later))
        val progress = afterRestart.refresh()
        assertEquals(50L, progress.totalXp)
        assertEquals(later, progress.categoryLastPracticedAt[tech])
    }

    @Test
    fun quizXp_isAwardedOncePerArticle_evenAfterTheProcessRestarts() = runBlocking {
        val (_, first) = repository.submitQuizResultOnce("art-int-q", tech, true)
        assertEquals(150L, first)

        val (progress, again) = restartedRepository().submitQuizResultOnce("art-int-q", tech, true)
        assertEquals(0L, again)
        assertEquals(150L, progress.totalXp)
        assertEquals(1, progress.quizzesPassed)
        assertEquals(1, progress.quizzesAttempted)
    }

    @Test
    fun aWrongQuizAnswer_leavesTheRewardAvailableForARetryAcrossARestart() = runBlocking {
        repository.submitQuizResultOnce("art-int-w", tech, false)

        val (progress, award) = restartedRepository().submitQuizResultOnce("art-int-w", tech, true)
        assertEquals(150L, award)
        assertEquals(150L, progress.totalXp)
        assertEquals(2, progress.quizzesAttempted)
    }

    @Test
    fun doubleXp_canBeClaimedOnlyOncePerArticle_evenAfterTheProcessRestarts() = runBlocking {
        repository.submitQuizResultOnce("art-int-d", tech, true)
        assertEquals(150L, repository.awardDoubleXpForQuizOnce("art-int-d", tech, 150L))
        assertTrue(repository.doubleRewardedArticleIds.value.contains("art-int-d"))

        val afterRestart = restartedRepository()
        afterRestart.refresh()
        assertTrue("the button must stay hidden after a restart", afterRestart.doubleRewardedArticleIds.value.contains("art-int-d"))
        assertEquals(0L, afterRestart.awardDoubleXpForQuizOnce("art-int-d", tech, 150L))
        assertEquals(300L, afterRestart.userProgress.value.totalXp)
    }

    @Test
    fun doubleXp_isRefusedUntilTheQuizWasAnsweredCorrectly() = runBlocking {
        assertEquals(0L, repository.awardDoubleXpForQuizOnce("art-int-nq", tech, 150L))
        repository.submitQuizResultOnce("art-int-nq", tech, false)
        assertEquals(0L, repository.awardDoubleXpForQuizOnce("art-int-nq", tech, 150L))
        assertEquals(0L, repository.refresh().totalXp)
    }

    @Test
    fun legacySharedPreferencesProgress_isImportedOnceAndLedgerBlocksReRewards() = runBlocking {
        legacyPrefs().edit()
            .putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 640L)
            .putLong(BrainRpgLegacyPreferences.categoryKey(tech), 320L)
            .putStringSet(BrainRpgLegacyPreferences.KEY_READ_REWARDED, setOf("legacy-art"))
            .commit()

        val imported = restartedRepository()
        val progress = imported.refresh()
        assertEquals(640L, progress.totalXp)
        assertEquals(320L, progress.categoryXp[tech])
        assertEquals("a legacy-rewarded article must not pay again", 0L, imported.awardReadingXpOnce("legacy-art", tech, 50L))

        // Later edits to the untouched legacy prefs must not be re-imported over Room.
        legacyPrefs().edit().putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 9_999L).commit()
        assertEquals(640L, restartedRepository().refresh().totalXp)
        assertTrue(restartedLedgerHas("legacy-art"))
    }

    private suspend fun restartedLedgerHas(articleId: String): Boolean {
        val db = databases.last()
        return db.brainRpgDao().hasReward(articleId, BrainRpgRewardType.READ)
    }
}
