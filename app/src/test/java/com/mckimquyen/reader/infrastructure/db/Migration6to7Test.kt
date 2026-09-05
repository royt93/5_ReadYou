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
class Migration6to7Test {

    @Test
    fun migration6to7_executesExpectedDdlStatements() {
        val mockDb = mockk<SupportSQLiteDatabase>(relaxed = true)
        MIGRATION_6_7.migrate(mockDb)

        verify(exactly = 1) {
            mockDb.execSQL("ALTER TABLE article ADD COLUMN aiSummary TEXT DEFAULT NULL")
            mockDb.execSQL("CREATE INDEX IF NOT EXISTS index_article_accountId_isUnread_date ON article(accountId, isUnread, date)")
            mockDb.execSQL("CREATE INDEX IF NOT EXISTS index_article_accountId_feedId_isUnread_date ON article(accountId, feedId, isUnread, date)")
            mockDb.execSQL("CREATE INDEX IF NOT EXISTS index_article_accountId_isStarred_date ON article(accountId, isStarred, date)")
        }
    }

    @Test
    fun migration6to7_liveSqlite_createsColumnAndIndices() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
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
                            updateAt INTEGER
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val supportDb = helper.writableDatabase

        // Thực hiện Migration 6 -> 7
        MIGRATION_6_7.migrate(supportDb)

        // Kiểm chứng cột aiSummary đã tồn tại và ghi/đọc được
        supportDb.execSQL(
            """
            INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId, aiSummary)
            VALUES ('art_1', 1000, 'Title 1', 'Raw', 'Short', 'https://example.com', 'feed_1', 1, 'Tóm tắt AI chuẩn xác')
            """.trimIndent()
        )

        val cursor = supportDb.query("SELECT aiSummary FROM article WHERE id = 'art_1'")
        assertTrue("Cursor phải có dữ liệu", cursor.moveToFirst())
        assertEquals("Tóm tắt AI chuẩn xác", cursor.getString(0))
        cursor.close()

        // Kiểm chứng 3 Composite Indices đã tồn tại trong sqlite_master
        val indexCursor = supportDb.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'article'"
        )
        val indexNames = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(0))
        }
        indexCursor.close()

        assertTrue(
            "Phải có index_article_accountId_isUnread_date",
            indexNames.contains("index_article_accountId_isUnread_date")
        )
        assertTrue(
            "Phải có index_article_accountId_feedId_isUnread_date",
            indexNames.contains("index_article_accountId_feedId_isUnread_date")
        )
        assertTrue(
            "Phải có index_article_accountId_isStarred_date",
            indexNames.contains("index_article_accountId_isStarred_date")
        )

        supportDb.close()
    }
}
