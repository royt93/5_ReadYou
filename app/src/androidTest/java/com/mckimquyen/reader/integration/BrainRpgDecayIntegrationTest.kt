package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrainRpgDecayIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: AndroidDatabase
    private lateinit var repository: BrainRpgRepository
    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dao = db.brainRpgDao()
        repository = BrainRpgRepository(context, db, dao,
            BrainRpgLegacyImporter(db, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao),
            scope, Dispatchers.IO)
    }

    @After fun tearDown() { scope.cancel(); db.close() }

    @Test fun projectedCategoryXpDecaysWhilePersistedBaseAndTotalRemainStable() = runBlocking {
        val category = QuizGeneratorService.CATEGORY_TECH
        val practiced = 1_800_000_000_000L
        repository.awardReadingXpOnce("decay-integration", category, 100L, practiced)
        val afterWeek = repository.refresh(practiced + 7L * 24L * 60L * 60L * 1_000L)

        assertEquals(90L, afterWeek.categoryXp[category])
        assertEquals(100L, afterWeek.categoryBaseXp[category])
        assertEquals(100L, afterWeek.totalXp)
        assertEquals(100L, db.brainRpgDao().getCategory(category)?.baseXp)
    }

    @Test fun recovery_resetsOnlyTheChosenCategory_keepsXpAndLevel() = runBlocking {
        val tech = QuizGeneratorService.CATEGORY_TECH
        val science = QuizGeneratorService.CATEGORY_SCIENCE
        val practiced = 1_800_000_000_000L
        val week = 7L * 24L * 60L * 60L * 1_000L
        repository.awardReadingXpOnce("rec-tech", tech, 100L, practiced)
        repository.awardReadingXpOnce("rec-sci", science, 100L, practiced)
        val before = repository.refresh(practiced + 2 * week)
        assertEquals(81L, before.categoryXp[tech])

        assertEquals(true, repository.recoverCategory(tech, practiced + 2 * week))

        val after = repository.refresh(practiced + 2 * week)
        assertEquals(100L, after.categoryXp[tech])
        assertEquals("the other category keeps decaying", 81L, after.categoryXp[science])
        assertEquals(before.totalXp, after.totalXp)
        assertEquals(before.level, after.level)
        assertEquals(100L, after.categoryBaseXp[tech])
    }

    @Test fun recovery_isRefusedWhenNothingDecayed_andSurvivesRepeatedCallbacks() = runBlocking {
        val tech = QuizGeneratorService.CATEGORY_TECH
        val practiced = 1_800_000_000_000L
        repository.awardReadingXpOnce("rec-fresh", tech, 100L, practiced)
        assertEquals(false, repository.recoverCategory(tech, practiced + 1_000L))
        val later = practiced + 3L * 7L * 24L * 60L * 60L * 1_000L
        assertEquals(true, repository.recoverCategory(tech, later))
        assertEquals(false, repository.recoverCategory(tech, later + 5_000L))
    }
}
