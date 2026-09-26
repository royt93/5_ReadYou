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
class Migration8to9Test {

    @Test
    fun migration8to9_executesExpectedDdlStatements() {
        val mockDb = mockk<SupportSQLiteDatabase>(relaxed = true)
        MIGRATION_8_9.migrate(mockDb)

        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("CREATE VIRTUAL TABLE IF NOT EXISTS `article_fts` USING FTS4") })
            mockDb.execSQL("INSERT INTO `article_fts`(`article_fts`) VALUES('rebuild')")
            mockDb.execSQL(match { it.contains("room_fts_content_sync_article_fts_BEFORE_UPDATE") })
            mockDb.execSQL(match { it.contains("room_fts_content_sync_article_fts_BEFORE_DELETE") })
            mockDb.execSQL(match { it.contains("room_fts_content_sync_article_fts_AFTER_UPDATE") })
            mockDb.execSQL(match { it.contains("room_fts_content_sync_article_fts_AFTER_INSERT") })
        }
    }

    @Test
    fun migration8to9_liveSqlite_createsFtsTableAndSyncTriggers() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE article (
                            id TEXT NOT NULL PRIMARY KEY,
                            date INTEGER NOT NULL,
                            title TEXT NOT NULL,
                            author TEXT,
                            rawDescription TEXT NOT NULL,
                            shortDescription TEXT NOT NULL,
                            fullContent TEXT,
                            img TEXT,
                            link TEXT NOT NULL,
                            feedId TEXT NOT NULL,
                            accountId INTEGER NOT NULL,
                            isUnread INTEGER NOT NULL DEFAULT 1,
                            isStarred INTEGER NOT NULL DEFAULT 0,
                            isReadLater INTEGER NOT NULL DEFAULT 0,
                            updateAt INTEGER,
                            aiSummary TEXT
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val supportDb = helper.writableDatabase

        // Pre-insert an article with Vietnamese diacritics before migration
        supportDb.execSQL(
            """
            INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId)
            VALUES ('art_pre', 1000, 'Tin tức Công nghệ AI', 'Raw', 'Phát triển trí tuệ nhân tạo', 'https://example.com/1', 'feed_1', 1)
            """.trimIndent()
        )

        // Perform Migration 8 -> 9
        MIGRATION_8_9.migrate(supportDb)

        // 1. Verify rebuild populated pre-existing article into FTS
        val preCursor = supportDb.query(
            "SELECT article.id FROM article JOIN article_fts ON article.rowid = article_fts.docid WHERE article_fts MATCH 'cong*'"
        )
        assertTrue("Pre-existing article must be searchable via FTS", preCursor.moveToFirst())
        assertEquals("art_pre", preCursor.getString(0))
        preCursor.close()

        // 2. Verify AFTER_INSERT trigger indexes new articles automatically
        supportDb.execSQL(
            """
            INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId)
            VALUES ('art_new', 2000, 'Khám phá Khoa học Không gian', 'Raw', 'Kính viễn vọng James Webb', 'https://example.com/2', 'feed_1', 1)
            """.trimIndent()
        )

        val newCursor = supportDb.query(
            "SELECT article.id FROM article JOIN article_fts ON article.rowid = article_fts.docid WHERE article_fts MATCH 'khong*'"
        )
        assertTrue("Newly inserted article must be indexed by trigger", newCursor.moveToFirst())
        assertEquals("art_new", newCursor.getString(0))
        newCursor.close()

        // 3. Verify AFTER_UPDATE trigger updates FTS entries
        supportDb.execSQL("UPDATE article SET title = 'Đột phá Quantum' WHERE id = 'art_new'")
        val updatedCursor = supportDb.query(
            "SELECT article.id FROM article JOIN article_fts ON article.rowid = article_fts.docid WHERE article_fts MATCH 'quantum*'"
        )
        assertTrue("Updated article must be searchable by new title", updatedCursor.moveToFirst())
        assertEquals("art_new", updatedCursor.getString(0))
        updatedCursor.close()

        // 4. Verify BEFORE_DELETE trigger cleans up FTS entries
        supportDb.execSQL("DELETE FROM article WHERE id = 'art_new'")
        val deletedCursor = supportDb.query(
            "SELECT article.id FROM article JOIN article_fts ON article.rowid = article_fts.docid WHERE article_fts MATCH 'quantum*'"
        )
        assertEquals("Deleted article must no longer match in FTS", 0, deletedCursor.count)
        deletedCursor.close()

        supportDb.close()
    }
}
