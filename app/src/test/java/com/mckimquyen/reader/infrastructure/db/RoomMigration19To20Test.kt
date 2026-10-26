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
class RoomMigration19To20Test {

    private fun openAtVersion19(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }

                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE article (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                    db.execSQL("INSERT INTO article (id, title) VALUES ('a1', 'Existing article')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration19to20_createsRadarTablesAndKeepsArticles() {
        val db = openAtVersion19()
        MIGRATION_19_20.migrate(db)

        assertEquals(1L, scalarLong(db, "SELECT COUNT(*) FROM article"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM article_snapshot"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM article_radar_state"))
        assertEquals(
            listOf("id", "articleId", "capturedAt", "titleHash", "contentHash", "title", "contentText"),
            columns(db, "article_snapshot"),
        )
        assertEquals(
            listOf("articleId", "editCount", "lastEditedAt", "missedSyncs", "removedAt"),
            columns(db, "article_radar_state"),
        )
        assertTrue(indices(db, "article_snapshot").contains("index_article_snapshot_articleId_capturedAt"))
        db.close()
    }

    @Test
    fun migration19to20_isIdempotent() {
        val db = openAtVersion19()
        MIGRATION_19_20.migrate(db)
        MIGRATION_19_20.migrate(db)
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM article_snapshot"))
        db.close()
    }

    @Test
    fun deletingArticleCascadesToSnapshotsAndState() {
        val db = openAtVersion19()
        MIGRATION_19_20.migrate(db)
        db.execSQL("INSERT INTO article_snapshot (articleId, capturedAt, titleHash, contentHash, title, contentText) VALUES ('a1', 1, 'h', 'h', 't', 'c')")
        db.execSQL("INSERT INTO article_radar_state (articleId, editCount, lastEditedAt, missedSyncs, removedAt) VALUES ('a1', 1, 1, 0, NULL)")

        db.execSQL("DELETE FROM article WHERE id='a1'")

        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM article_snapshot"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM article_radar_state"))
        db.close()
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun indices(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun scalarLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }
}
