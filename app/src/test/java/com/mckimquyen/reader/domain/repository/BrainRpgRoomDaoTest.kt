package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.domain.model.rpg.BrainRpgXpEventEntity
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BrainRpgRoomDaoTest {
    private lateinit var db: AndroidDatabase
    private lateinit var dao: BrainRpgDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.brainRpgDao()
    }
    @After fun tearDown() = db.close()

    @Test fun rewardClaim_isUniquePerArticleAndType() = runBlocking {
        val read = BrainRpgRewardEntity(articleId = "a1", rewardType = BrainRpgRewardType.READ, createdAtMillis = 1L)
        val quiz = BrainRpgRewardEntity(articleId = "a1", rewardType = BrainRpgRewardType.QUIZ, createdAtMillis = 2L)
        assertTrue(dao.claimReward(read))
        assertFalse(dao.claimReward(read.copy(createdAtMillis = 3L)))
        assertTrue(dao.claimReward(quiz))
        assertEquals(setOf("a1" to BrainRpgRewardType.READ, "a1" to BrainRpgRewardType.QUIZ), dao.allRewards().map { it.articleId to it.rewardType }.toSet())
    }

    @Test fun profileAndCategoryUpsertReadPersistedState() = runBlocking {
        val profile = BrainRpgProfileEntity(id = 1, baseTotalXp = 250L, streakDays = 3)
        val category = BrainRpgCategoryEntity("legacy_custom", 90L, 123L, 4L)
        dao.upsertProfile(profile)
        dao.upsertCategory(category)
        assertEquals(profile, dao.getProfile())
        assertEquals(category, dao.getCategory("legacy_custom"))
    }

    @Test fun failedRoomTransaction_rollsBackLedgerAndProfile() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        val claim = BrainRpgRewardEntity(articleId = "a2", rewardType = BrainRpgRewardType.READ, createdAtMillis = 1L)
        runCatching { db.withTransaction { check(dao.claimReward(claim)); dao.updateProfile(requireNotNull(dao.getProfile()).copy(baseTotalXp = 50L)); error("simulated failure") } }
        assertEquals(emptyList<BrainRpgRewardEntity>(), dao.allRewards())
        assertEquals(0L, dao.getProfile()?.baseTotalXp)
    }

    @Test fun importMarkerIsStoredWithProfile() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        assertFalse(requireNotNull(dao.getProfile()).legacyImportComplete)
        dao.updateProfile(requireNotNull(dao.getProfile()).copy(legacyImportComplete = true))
        assertTrue(requireNotNull(dao.getProfile()).legacyImportComplete)
    }

    @Test fun repeatRead_claimDoesNotReawardButAlwaysRefreshesPracticeTimestamp() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        dao.upsertCategory(BrainRpgCategoryEntity("tech", baseXp = 250L, lastPracticedAtMillis = 100L, lastDecayNotificationEpochDay = 7L))
        val reward = BrainRpgRewardEntity(articleId = "a-read", rewardType = BrainRpgRewardType.READ, createdAtMillis = 100L)
        assertTrue(dao.claimAndApplyReward(reward, "tech", 100L, 50L))
        assertFalse(dao.claimAndApplyReward(reward.copy(createdAtMillis = 200L), "tech", 200L, 50L))
        val category = requireNotNull(dao.getCategory("tech"))
        assertEquals(300L, category.baseXp)
        assertEquals(200L, category.lastPracticedAtMillis)
        assertEquals(7L, category.lastDecayNotificationEpochDay)
        val events = dao.allXpEvents()
        assertEquals(1, events.size)
        assertEquals("tech", events.single().categoryId)
        assertEquals(50L, events.single().xpDelta)
        assertEquals(BrainRpgRewardType.READ, events.single().rewardType)
        assertEquals(100L, events.single().createdAtMillis)
    }

    @Test fun duplicateCorrectQuiz_refreshesPracticeButNotXpOrCounters() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        val reward = BrainRpgRewardEntity(articleId = "a-quiz", rewardType = BrainRpgRewardType.QUIZ, createdAtMillis = 10L)
        assertTrue(dao.claimAndApplyReward(reward, "tech", 10L, 150L, quizAttemptDelta = 1, quizPassDelta = 1))
        assertFalse(dao.claimAndApplyReward(reward.copy(createdAtMillis = 20L), "tech", 20L, 150L, quizAttemptDelta = 1, quizPassDelta = 1))
        val p = requireNotNull(dao.getProfile())
        assertEquals(150L, p.baseTotalXp)
        assertEquals(1, p.quizzesAttempted)
        assertEquals(1, p.quizzesPassed)
        assertEquals(20L, dao.getCategory("tech")?.lastPracticedAtMillis)
        assertEquals(1, dao.allXpEvents().size)
        assertEquals(150L, dao.allXpEvents().single().xpDelta)
    }

    @Test fun wrongQuizAttempt_incrementsAttemptOnly() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        dao.recordWrongQuizAttempt()
        val p = requireNotNull(dao.getProfile())
        assertEquals(1, p.quizzesAttempted)
        assertEquals(0, p.quizzesPassed)
        assertEquals(0L, p.baseTotalXp)
        assertTrue(dao.allXpEvents().isEmpty())
        assertFalse(dao.hasReward("a-wrong", BrainRpgRewardType.QUIZ))
        assertEquals(null, dao.getCategory("tech"))
    }

    @Test fun failedAwardTransaction_rollsBackLedgerProfileAndCategory() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        runCatching { dao.claimAndApplyReward(BrainRpgRewardEntity(articleId = "a-r", rewardType = BrainRpgRewardType.READ, createdAtMillis = 10L), "tech", 10L, 50L, failBeforeCommit = true) }
        assertFalse(dao.hasReward("a-r", BrainRpgRewardType.READ))
        assertEquals(0L, dao.getProfile()?.baseTotalXp)
        assertEquals(null, dao.getCategory("tech"))
        assertTrue(dao.allXpEvents().isEmpty())
    }

    @Test fun independentDecayNotificationUpdate_preservesBaseXpAndPracticeTime() = runBlocking {
        dao.upsertCategory(BrainRpgCategoryEntity("tech", baseXp = 500L, lastPracticedAtMillis = 123L))
        dao.updateDecayNotificationDay("tech", 42L)
        val row = requireNotNull(dao.getCategory("tech"))
        assertEquals(500L, row.baseXp)
        assertEquals(123L, row.lastPracticedAtMillis)
        assertEquals(42L, row.lastDecayNotificationEpochDay)
    }

    @Test fun doubleRewardClaimAndXpAreAtomicAndOnlyOnce() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        dao.upsertCategory(BrainRpgCategoryEntity("tech"))
        dao.claimReward(BrainRpgRewardEntity(articleId = "q1", rewardType = BrainRpgRewardType.QUIZ, createdAtMillis = 1L))
        assertTrue(dao.claimDoubleRewardAndApplyXp(BrainRpgRewardEntity(articleId = "q1", rewardType = BrainRpgRewardType.DOUBLE, createdAtMillis = 2L), "tech", 150L))
        assertFalse(dao.claimDoubleRewardAndApplyXp(BrainRpgRewardEntity(articleId = "q1", rewardType = BrainRpgRewardType.DOUBLE, createdAtMillis = 3L), "tech", 150L))
        assertEquals(150L, dao.getProfile()?.baseTotalXp)
        assertEquals(150L, dao.getCategory("tech")?.baseXp)
        assertEquals(1, dao.allXpEvents().size)
        assertEquals(BrainRpgRewardType.DOUBLE, dao.allXpEvents().single().rewardType)
    }

    @Test fun doubleReward_requiresCorrectQuizLedger() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        dao.upsertCategory(BrainRpgCategoryEntity("tech"))
        assertFalse(dao.claimDoubleRewardAndApplyXp(BrainRpgRewardEntity(articleId = "q2", rewardType = BrainRpgRewardType.DOUBLE, createdAtMillis = 2L), "tech", 150L))
        assertEquals(0L, dao.getProfile()?.baseTotalXp)
        assertTrue(dao.allXpEvents().isEmpty())
        assertFalse(dao.hasReward("q2", BrainRpgRewardType.DOUBLE))
    }
}
