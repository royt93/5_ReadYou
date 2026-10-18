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
class RoomMigration16To17Test {

    private fun openAtVersion16(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE article (id TEXT NOT NULL PRIMARY KEY, accountId INTEGER NOT NULL, isUnread INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO article (id, accountId, isUnread) VALUES ('a1', 1, 0)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration16to17_addsNullableReadAtAndKeepsExistingRows() {
        val db = openAtVersion16()
        MIGRATION_16_17.migrate(db)

        val cursor = db.query("SELECT id, accountId, isUnread, readAt FROM article WHERE id = 'a1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("a1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("accountId")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isUnread")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("readAt")))
        cursor.close()
        db.close()
    }

    @Test
    fun migration16to17_readAtAcceptsEpochMillis() {
        val db = openAtVersion16()
        MIGRATION_16_17.migrate(db)

        db.execSQL("UPDATE article SET readAt = $EPOCH_MILLIS WHERE id = 'a1'")

        val cursor = db.query("SELECT readAt FROM article WHERE id = 'a1'")
        assertTrue(cursor.moveToFirst())
        assertEquals(EPOCH_MILLIS, cursor.getLong(0))
        cursor.close()
        db.close()
    }

    private companion object {
        const val EPOCH_MILLIS = 1_700_000_000_000L
    }
}
