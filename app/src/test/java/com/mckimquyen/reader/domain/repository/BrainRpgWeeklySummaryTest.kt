package com.mckimquyen.reader.domain.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.domain.model.rpg.BrainRpgXpEventEntity
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainRpgWeeklySummaryTest {

    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(
            database,
            context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE),
            dao,
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        repository = BrainRpgRepository(context, database, dao, importer, scope, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun weeklySummary_usesHalfOpenIntervalAndKeepsCurrentStreak() = runBlocking {
        val zone = ZoneId.of("UTC")
        val interval = com.mckimquyen.reader.domain.model.rpg.BrainRpgWeek.interval(
            Instant.parse("2026-10-20T12:00:00Z"), zone,
        )
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 6))
        listOf(
            BrainRpgXpEventEntity(categoryId = "OutsideBefore", xpDelta = 999L, rewardType = BrainRpgRewardType.READ, createdAtMillis = interval.startMillis - 1),
            BrainRpgXpEventEntity(categoryId = "Science", xpDelta = 100L, rewardType = BrainRpgRewardType.READ, createdAtMillis = interval.startMillis),
            BrainRpgXpEventEntity(categoryId = "Tech", xpDelta = 100L, rewardType = BrainRpgRewardType.QUIZ, createdAtMillis = interval.startMillis + 1),
            BrainRpgXpEventEntity(categoryId = "Science", xpDelta = 50L, rewardType = BrainRpgRewardType.TRIAGE, createdAtMillis = interval.endMillis - 1),
            BrainRpgXpEventEntity(categoryId = "OutsideAfter", xpDelta = 500L, rewardType = BrainRpgRewardType.DOUBLE, createdAtMillis = interval.endMillis),
        ).forEach { dao.insertXpEvent(it) }

        val summary = repository.getWeeklyBrainWrapped(
            now = Instant.parse("2026-10-20T12:00:00Z"),
            zone = zone,
        )

        assertEquals(interval.startMillis, summary.weekStartMillis)
        assertEquals(interval.endMillis, summary.weekEndMillis)
        assertEquals(250L, summary.weeklyXp)
        assertEquals("Science", summary.topCategoryId)
        assertEquals(6, summary.currentStreakDays)
    }

    @Test
    fun weeklySummary_tieBreaksByCategoryIdAscending() = runBlocking {
        val now = Instant.parse("2026-10-20T12:00:00Z")
        val interval = com.mckimquyen.reader.domain.model.rpg.BrainRpgWeek.interval(now, ZoneId.of("UTC"))
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        dao.insertXpEvent(BrainRpgXpEventEntity(categoryId = "Science", xpDelta = 100L, rewardType = BrainRpgRewardType.READ, createdAtMillis = interval.startMillis))
        dao.insertXpEvent(BrainRpgXpEventEntity(categoryId = "Business", xpDelta = 100L, rewardType = BrainRpgRewardType.QUIZ, createdAtMillis = interval.startMillis + 1))

        val summary = repository.getWeeklyBrainWrapped(now, ZoneId.of("UTC"))

        assertEquals(200L, summary.weeklyXp)
        assertEquals("Business", summary.topCategoryId)
    }

    @Test
    fun weeklySummary_emptyWeekReturnsZeroXpAndNullTopic() = runBlocking {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 4))

        val summary = repository.getWeeklyBrainWrapped(
            now = Instant.parse("2026-10-20T12:00:00Z"),
            zone = ZoneId.of("UTC"),
        )

        assertEquals(0L, summary.weeklyXp)
        assertEquals(null, summary.topCategoryId)
        assertEquals(4, summary.currentStreakDays)
    }
}
