package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
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

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.brainRpgDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertsAndReadsSingletonProfile() = runBlocking {
        val profile = BrainRpgProfileEntity(id = PROFILE_ID, baseTotalXp = 250L, streakDays = 3)
        dao.upsertProfile(profile)
        assertEquals(profile, dao.getProfile())
    }

    @Test
    fun upsertsCategoryAndReadsItsPracticeTimestamp() = runBlocking {
        val row = BrainRpgCategoryEntity(categoryId = "legacy_custom", baseXp = 90L, lastPracticedAtMillis = 123L)
        dao.upsertCategory(row)
        assertEquals(row, dao.getCategory("legacy_custom"))
    }

    @Test
    fun rewardClaim_isInsertedOncePerArticleAndType_butOtherTypesAreIndependent() = runBlocking {
        val read = BrainRpgRewardEntity(articleId = "a1", rewardType = BrainRpgRewardType.READ, createdAtMillis = 1L)
        val quiz = BrainRpgRewardEntity(articleId = "a1", rewardType = BrainRpgRewardType.QUIZ, createdAtMillis = 2L)
        assertTrue(dao.claimReward(read))
        assertFalse(dao.claimReward(read.copy(createdAtMillis = 3L)))
        assertTrue(dao.claimReward(quiz))
        assertEquals(setOf("a1" to BrainRpgRewardType.READ, "a1" to BrainRpgRewardType.QUIZ), dao.allRewards().map { it.articleId to it.rewardType }.toSet())
    }

    @Test
    fun rewardAndProfileUpdate_areOneTransaction_andRollbackTogether() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = PROFILE_ID))
        val claim = BrainRpgRewardEntity(articleId = "a2", rewardType = BrainRpgRewardType.READ, createdAtMillis = 1L)

        runCatching {
            db.withTransaction {
                check(dao.claimReward(claim))
                dao.updateProfile(requireNotNull(dao.getProfile()).copy(baseTotalXp = 50L))
                error("simulated failure before commit")
            }
        }

        assertEquals("failed transaction does not leave a claim", emptyList<BrainRpgRewardEntity>(), dao.allRewards())
        assertEquals("failed transaction does not leave XP", 0L, dao.getProfile()?.baseTotalXp)
    }

    @Test
    fun importMarkerIsStoredWithTheProfile() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = PROFILE_ID, legacyImportComplete = false))
        assertFalse(requireNotNull(dao.getProfile()).legacyImportComplete)
        dao.updateProfile(requireNotNull(dao.getProfile()).copy(legacyImportComplete = true))
        assertTrue(requireNotNull(dao.getProfile()).legacyImportComplete)
    }

    private companion object {
        const val PROFILE_ID = 1
    }
}
