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
class RoomMigration14To15Test {

    @Test
    fun migration14to15_createsArticleReadPositionTableAndIndices() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        MIGRATION_14_15.migrate(db)

        db.execSQL(
            """
            INSERT INTO article_read_position (articleId, accountId, progress, lastReadTime, isSynced)
            VALUES ('art_123', 1, 0.65, 1700000000000, 0)
            """.trimIndent()
        )

        val cursor = db.query("SELECT * FROM article_read_position WHERE articleId = 'art_123'")
        assertTrue(cursor.moveToFirst())
        assertEquals("art_123", cursor.getString(cursor.getColumnIndexOrThrow("articleId")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("accountId")))
        assertEquals(0.65f, cursor.getFloat(cursor.getColumnIndexOrThrow("progress")), 0.001f)
        assertEquals(1700000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastReadTime")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")))
        cursor.close()
        db.close()
    }
}
