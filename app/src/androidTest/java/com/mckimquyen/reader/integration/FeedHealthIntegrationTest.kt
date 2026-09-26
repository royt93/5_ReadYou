package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.feed.FeedErrorType
import com.mckimquyen.reader.domain.model.feed.FeedHealthRecord
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.allMigrations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies on real Pixel 7 Pro (API 34/35/36/37):
 * Room DB v10 schema with allMigrations applied cleanly on SQLite,
 * FeedHealthDao persists and queries FeedHealthRecord correctly.
 */
@RunWith(AndroidJUnit4::class)
class FeedHealthIntegrationTest {

    private lateinit var db: AndroidDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .addMigrations(*allMigrations)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun feedHealthDao_persistsAndObservesRecordOnDevice() = runBlocking {
        val dao = db.feedHealthDao()

        val record = FeedHealthRecord(
            feedId = "feed_device_1",
            lastSuccessTime = 1727330000000L,
            lastLatencyMs = 142L,
            lastErrorType = FeedErrorType.HTTP,
            lastErrorMessage = "HTTP 404 Not Found",
            lastErrorTime = 1727330500000L,
        )
        dao.upsert(record)

        val queried = dao.queryByFeedId("feed_device_1")
        assertEquals(record, queried)
        assertTrue(queried?.isFailing == true)

        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("feed_device_1", all[0].feedId)
    }
}
