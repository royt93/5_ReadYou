package com.mckimquyen.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration9to10Test {

    @Test
    fun migration9to10_createsPersistentFeedHealthTable() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        MIGRATION_9_10.migrate(db)

        db.execSQL(
            """
            INSERT INTO feed_health_record
            (feedId, lastSuccessTime, lastLatencyMs, lastErrorType, lastErrorMessage, lastErrorTime)
            VALUES ('feed_1', 1000, 250, 'TIMEOUT', 'Read timed out', 2000)
            """.trimIndent()
        )
        val cursor = db.query("SELECT * FROM feed_health_record WHERE feedId = 'feed_1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("feed_1", cursor.getString(cursor.getColumnIndexOrThrow("feedId")))
        assertEquals(250L, cursor.getLong(cursor.getColumnIndexOrThrow("lastLatencyMs")))
        assertEquals("TIMEOUT", cursor.getString(cursor.getColumnIndexOrThrow("lastErrorType")))
        cursor.close()
        db.close()
    }
}
