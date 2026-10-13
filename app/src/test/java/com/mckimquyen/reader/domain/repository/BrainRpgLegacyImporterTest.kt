package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.Dispatchers
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BrainRpgLegacyImporterTest {
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

    private fun importer(prefs: android.content.SharedPreferences) = BrainRpgLegacyImporter(db, prefs, dao)

    private fun legacyPrefs() = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        .apply { edit().clear().commit() }

    @Test
    fun firstImport_preservesProfileAllCategoriesAndRewardLedger() = runTest {
        val prefs = legacyPrefs()
        prefs.edit()
            .putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 1_250L)
            .putInt(BrainRpgLegacyPreferences.KEY_STREAK_DAYS, 4)
            .putLong(BrainRpgLegacyPreferences.KEY_LAST_READ_EPOCH_DAY, 20L)
            .putBoolean(BrainRpgLegacyPreferences.KEY_STREAK_SHIELD_ACTIVE, true)
            .putInt(BrainRpgLegacyPreferences.KEY_QUIZZES_ATTEMPTED, 10)
            .putInt(BrainRpgLegacyPreferences.KEY_QUIZZES_PASSED, 7)
            .putLong(BrainRpgLegacyPreferences.categoryKey("tech"), 111L)
            .putLong(BrainRpgLegacyPreferences.categoryKey("retired"), 777L)
            .putStringSet(BrainRpgLegacyPreferences.KEY_READ_REWARDED, setOf("a1"))
            .putStringSet(BrainRpgLegacyPreferences.KEY_QUIZ_REWARDED, setOf("a1"))
            .putStringSet(BrainRpgLegacyPreferences.KEY_DOUBLE_REWARDED, setOf("a1"))
            .commit()

        val imported = importer(prefs).ensureImported(nowMillis = MIGRATION_NOW)

        assertTrue(imported)
        assertEquals(1_250L, dao.getProfile()?.baseTotalXp)
        assertEquals(4, dao.getProfile()?.streakDays)
        assertEquals(20L, dao.getProfile()?.lastReadEpochDay)
        assertTrue(dao.getProfile()?.streakShieldActive == true)
        assertEquals(10, dao.getProfile()?.quizzesAttempted)
        assertEquals(7, dao.getProfile()?.quizzesPassed)
        assertTrue(dao.getProfile()?.legacyImportComplete == true)
        assertEquals(111L, dao.getCategory("tech")?.baseXp)
        assertEquals(777L, dao.getCategory("retired")?.baseXp)
        assertEquals(MIGRATION_NOW, dao.getCategory("tech")?.lastPracticedAtMillis)
        assertEquals(
            setOf(
                "a1" to BrainRpgRewardType.READ,
                "a1" to BrainRpgRewardType.QUIZ,
                "a1" to BrainRpgRewardType.DOUBLE,
            ),
            dao.allRewards().map { it.articleId to it.rewardType }.toSet(),
        )
        assertEquals("legacy preferences are retained for rollback", 1_250L, prefs.getLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 0L))
    }

    @Test
    fun missingPrefs_importsExistingDefaultsAndStillSetsTheMarker() = runTest {
        val prefs = legacyPrefs()
        importer(prefs).ensureImported(nowMillis = MIGRATION_NOW)

        assertEquals(0L, dao.getProfile()?.baseTotalXp)
        assertEquals(BrainRpgProfileEntity.DEFAULT_STREAK_DAYS, dao.getProfile()?.streakDays)
        assertEquals(0, dao.getProfile()?.quizzesAttempted)
        assertTrue(dao.getProfile()?.legacyImportComplete == true)
        assertTrue(dao.getCategories().isEmpty())
        assertTrue(dao.allRewards().isEmpty())
    }

    @Test
    fun repeatedImport_isANoopAndNeverOverwritesRoomProgress() = runTest {
        val prefs = legacyPrefs().apply { edit().putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 50L).commit() }
        val target = importer(prefs)
        assertTrue(target.ensureImported(nowMillis = MIGRATION_NOW))
        dao.upsertProfile(requireNotNull(dao.getProfile()).copy(baseTotalXp = 900L))

        assertFalse(target.ensureImported(nowMillis = MIGRATION_NOW + 1L))
        assertEquals(900L, dao.getProfile()?.baseTotalXp)
    }

    @Test
    fun importFailureBeforeCommit_rollsBackMarkerAndRows_thenRetrySucceeds() = runTest {
        val prefs = legacyPrefs().apply {
            edit().putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 321L)
                .putLong(BrainRpgLegacyPreferences.categoryKey("tech"), 321L).commit()
        }
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_rpg_category BEFORE INSERT ON brain_rpg_category BEGIN SELECT RAISE(ABORT, 'forced import failure'); END"
        )
        val target = importer(prefs)

        runCatching { target.ensureImported(nowMillis = MIGRATION_NOW) }
        assertFalse(dao.getProfile()?.legacyImportComplete == true)
        assertEquals(null, dao.getProfile())

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_rpg_category")
        assertTrue(target.ensureImported(nowMillis = MIGRATION_NOW))
        assertEquals(321L, dao.getProfile()?.baseTotalXp)
        assertTrue(dao.getProfile()?.legacyImportComplete == true)
    }

    @Test
    fun concurrentFirstAccess_importsOnceWithoutLosingRows() = runTest {
        val prefs = legacyPrefs().apply {
            edit().putLong(BrainRpgLegacyPreferences.KEY_TOTAL_XP, 400L)
                .putLong(BrainRpgLegacyPreferences.categoryKey("tech"), 400L).commit()
        }
        val target = importer(prefs)
        val outcomes = listOf(
            async(Dispatchers.Default) { target.ensureImported(nowMillis = MIGRATION_NOW) },
            async(Dispatchers.Default) { target.ensureImported(nowMillis = MIGRATION_NOW) },
        ).map { it.await() }

        assertEquals("exactly one concurrent first-access call imports", 1, outcomes.count { it })
        assertEquals("the other call observes the durable marker", 1, outcomes.count { !it })
        assertEquals(400L, dao.getProfile()?.baseTotalXp)
        assertEquals(400L, dao.getCategory("tech")?.baseXp)
        assertTrue(dao.getProfile()?.legacyImportComplete == true)
    }

    private companion object {
        const val MIGRATION_NOW = 1_800_000_000_000L
    }
}
