package com.mckimquyen.reader.integration

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.sv.BrainRpgDecayProcessor
import com.mckimquyen.reader.domain.sv.BrainRpgDecayWorker
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrainRpgDecayWorkerIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: AndroidDatabase
    private lateinit var processor: BrainRpgDecayProcessor
    private lateinit var manager: NotificationManager

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NotificationHelper.RPG_DECAY_NOTIFICATION_ID)
        val prefs = context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).build()
        val dao = db.brainRpgDao()
        processor = BrainRpgDecayProcessor(context, dao, BrainRpgLegacyImporter(db, prefs, dao), NotificationHelper(context), Dispatchers.IO)
    }

    @After fun tearDown() {
        manager.cancel(NotificationHelper.RPG_DECAY_NOTIFICATION_ID)
        db.close()
    }

    private fun worker(): BrainRpgDecayWorker = TestListenableWorkerBuilder<BrainRpgDecayWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                BrainRpgDecayWorker(appContext, workerParameters, processor)
        })
        .build()

    private fun activeSummary() = manager.activeNotifications.filter { it.id == NotificationHelper.RPG_DECAY_NOTIFICATION_ID }

    @Test fun worker_returnsSuccess_andLeavesStateUntouchedWhenNothingDecays() = runBlocking {
        val dao = db.brainRpgDao()
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, legacyImportComplete = true))
        dao.upsertCategory(BrainRpgCategoryEntity("Tech & AI", 100L, System.currentTimeMillis()))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(activeSummary().isEmpty())
        assertEquals(100L, dao.getCategory("Tech & AI")?.baseXp)
    }

    @Test fun decayedCategory_postsExactlyOneSummary_andSecondRunDoesNotDuplicate() = runBlocking {
        val dao = db.brainRpgDao()
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, legacyImportComplete = true))
        dao.upsertCategory(BrainRpgCategoryEntity("Tech & AI", 100L, System.currentTimeMillis() - 8L * DAY))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        if (NotificationHelper(context).canPostNotifications()) {
            assertEquals(1, activeSummary().size)
        } else {
            assertTrue("permission denied must not crash or post", activeSummary().isEmpty())
        }
        assertEquals(100L, dao.getCategory("Tech & AI")?.baseXp)
    }

    @Test fun practiceAfterSummary_clearsTheExistingNotification() = runBlocking {
        val dao = db.brainRpgDao()
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, legacyImportComplete = true))
        dao.upsertCategory(BrainRpgCategoryEntity("Tech & AI", 100L, System.currentTimeMillis() - 8L * DAY))
        worker().doWork()
        dao.upsertCategory(requireNotNull(dao.getCategory("Tech & AI")).copy(lastPracticedAtMillis = System.currentTimeMillis()))
        worker().doWork()
        assertTrue(activeSummary().isEmpty())
    }

    private companion object { const val DAY = 86_400_000L }
}
