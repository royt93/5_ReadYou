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
class RoomMigration17To18Test {

    private fun openAtVersion17(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
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
    fun migration17to18_addsNullableAudioColumnsAndKeepsExistingRows() {
        val db = openAtVersion17()
        MIGRATION_17_18.migrate(db)

        val cursor = db.query("SELECT id, accountId, isUnread, audioUrl, audioMime, audioDurationSec FROM article WHERE id = 'a1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("a1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("accountId")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isUnread")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("audioUrl")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("audioMime")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("audioDurationSec")))
        cursor.close()
        db.close()
    }

    @Test
    fun migration17to18_audioColumnsAcceptValues() {
        val db = openAtVersion17()
        MIGRATION_17_18.migrate(db)

        db.execSQL("UPDATE article SET audioUrl = '$URL', audioMime = 'audio/mpeg', audioDurationSec = $DURATION WHERE id = 'a1'")

        val cursor = db.query("SELECT audioUrl, audioMime, audioDurationSec FROM article WHERE id = 'a1'")
        assertTrue(cursor.moveToFirst())
        assertEquals(URL, cursor.getString(0))
        assertEquals("audio/mpeg", cursor.getString(1))
        assertEquals(DURATION, cursor.getInt(2))
        cursor.close()
        db.close()
    }

    private companion object {
        const val URL = "https://x/a.mp3"
        const val DURATION = 90
    }
}
