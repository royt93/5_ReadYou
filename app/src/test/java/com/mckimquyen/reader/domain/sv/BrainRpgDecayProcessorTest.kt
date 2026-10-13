package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
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
@Config(sdk = [34], application = Application::class)
class BrainRpgDecayProcessorTest {
    private lateinit var context: Context
    private lateinit var db: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var notifications: NotificationHelper
    private lateinit var processor: BrainRpgDecayProcessor

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        dao = db.brainRpgDao()
        notifications = mockk(relaxed = true)
        every { notifications.canPostNotifications() } returns true
        processor = BrainRpgDecayProcessor(context, dao, BrainRpgLegacyImporter(db, prefs, dao), notifications, Dispatchers.Unconfined)
    }

    @After fun tearDown() = db.close()

    @Test fun freshCategory_cancelsSummaryAndDoesNotNotify() = runBlocking {
        seed("Tech & AI", practicedAt = NOW - 6 * DAY)
        val result = processor.run(NOW)
        assertTrue(result.cancelled)
        assertFalse(result.notified)
        verify(exactly = 1) { notifications.cancelRpgDecay() }
        verify(exactly = 0) { notifications.notifyRpgDecay(any(), any()) }
    }

    @Test fun decayingCategory_postsOneSummaryAndRecordsToday() = runBlocking {
        seed("Tech & AI", practicedAt = NOW - 8 * DAY)
        seed("Business & Finance", practicedAt = NOW - 15 * DAY)
        val result = processor.run(NOW)
        assertTrue(result.notified)
        assertEquals(2, result.decayingCount)
        verify(exactly = 1) { notifications.notifyRpgDecay(any(), any()) }
        val today = NOW / DAY
        assertEquals(today, dao.getCategory("Tech & AI")?.lastDecayNotificationEpochDay)
        assertEquals(today, dao.getCategory("Business & Finance")?.lastDecayNotificationEpochDay)
    }

    @Test fun workerRetryOrSecondRunOnSameDay_doesNotRepost() = runBlocking {
        seed("Tech & AI", practicedAt = NOW - 8 * DAY)
        processor.run(NOW)
        val second = processor.run(NOW + 60_000L)
        assertFalse(second.notified)
        verify(exactly = 1) { notifications.notifyRpgDecay(any(), any()) }
    }

    @Test fun nextDay_postsAgainUntilPracticeHappens() = runBlocking {
        seed("Tech & AI", practicedAt = NOW - 8 * DAY)
        processor.run(NOW)
        processor.run(NOW + DAY)
        verify(exactly = 2) { notifications.notifyRpgDecay(any(), any()) }
    }

    @Test fun practiceAfterNotification_cancelsExistingSummary() = runBlocking {
        seed("Tech & AI", practicedAt = NOW - 8 * DAY)
        processor.run(NOW)
        dao.upsertCategory(requireNotNull(dao.getCategory("Tech & AI")).copy(lastPracticedAtMillis = NOW + 1_000L))
        val result = processor.run(NOW + DAY)
        assertTrue(result.cancelled)
        verify(exactly = 1) { notifications.cancelRpgDecay() }
    }

    @Test fun notificationPermissionDenied_doesNotCrashOrConsumeTodaysNotification() = runBlocking {
        every { notifications.canPostNotifications() } returns false
        seed("Tech & AI", practicedAt = NOW - 8 * DAY)
        val result = processor.run(NOW)
        assertFalse(result.notified)
        assertEquals(0L, dao.getCategory("Tech & AI")?.lastDecayNotificationEpochDay)
        verify(exactly = 0) { notifications.notifyRpgDecay(any(), any()) }
    }

    @Test fun processorDoesNotMutateEarnedXpOrPracticeTime() = runBlocking {
        seed("Tech & AI", practicedAt = NOW - 8 * DAY, xp = 321L)
        processor.run(NOW)
        val category = requireNotNull(dao.getCategory("Tech & AI"))
        assertEquals(321L, category.baseXp)
        assertEquals(NOW - 8 * DAY, category.lastPracticedAtMillis)
    }

    private suspend fun seed(categoryId: String, practicedAt: Long, xp: Long = 100L) {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, legacyImportComplete = true))
        dao.upsertCategory(BrainRpgCategoryEntity(categoryId, xp, practicedAt))
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_800_000_000_000L
    }
}
