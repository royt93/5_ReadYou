package com.mckimquyen.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration7to8Test {

    @Test
    fun migration7to8_executesExpectedDdlStatements() {
        val mockDb = mockk<SupportSQLiteDatabase>(relaxed = true)
        MIGRATION_7_8.migrate(mockDb)

        verify(atLeast = 1) {
            mockDb.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS article_highlight_note") })
            mockDb.execSQL("CREATE INDEX IF NOT EXISTS index_article_highlight_note_articleId ON article_highlight_note(articleId)")
            mockDb.execSQL("CREATE INDEX IF NOT EXISTS index_article_highlight_note_createdAt ON article_highlight_note(createdAt)")
        }
    }

    @Test
    fun migration7to8_liveSqlite_createsTableAndIndices() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE article (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 7 && newVersion == 8) {
                        MIGRATION_7_8.migrate(db)
                    }
                }
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Trigger upgrade by setting version 8
        MIGRATION_7_8.migrate(db)

        // Verify table exists by inserting a row
        db.execSQL(
            """
            INSERT INTO article_highlight_note (
                id, articleId, articleTitle, feedName, articleLink, selectedText, noteComment, colorHex, createdAt
            ) VALUES (
                'test-id', 'art-1', 'Test Title', 'Tech Feed', 'https://example.com', 'Crucial quote', 'My note', '#FFF176', 1700000000000
            )
            """.trimIndent()
        )

        val cursor = db.query("SELECT id, articleTitle, selectedText, noteComment, colorHex FROM article_highlight_note WHERE id = 'test-id'")
        assertTrue(cursor.moveToFirst())
        assertEquals("test-id", cursor.getString(0))
        assertEquals("Test Title", cursor.getString(1))
        assertEquals("Crucial quote", cursor.getString(2))
        assertEquals("My note", cursor.getString(3))
        assertEquals("#FFF176", cursor.getString(4))
        cursor.close()

        // Verify indices exist
        val indexCursor = db.query("PRAGMA index_list('article_highlight_note')")
        val indices = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(1))
        }
        indexCursor.close()

        assertTrue(indices.contains("index_article_highlight_note_articleId"))
        assertTrue(indices.contains("index_article_highlight_note_createdAt"))

        helper.close()
    }
}
