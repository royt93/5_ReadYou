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
class RoomMigration15To16Test {

    private fun openAtVersion15(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration15to16_createsSmartCollectionTable() {
        val db = openAtVersion15()
        MIGRATION_15_16.migrate(db)

        db.execSQL(
            """
            INSERT INTO smart_collection (id, accountId, name, query, minScoreThreshold, createdAt)
            VALUES ('col-1', 0, 'AI News', 'artificial intelligence', 0.22, 1700000000000)
            """.trimIndent()
        )

        val cursor = db.query("SELECT * FROM smart_collection WHERE id = 'col-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("col-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("accountId")))
        assertEquals("AI News", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals("artificial intelligence", cursor.getString(cursor.getColumnIndexOrThrow("query")))
        assertEquals(0.22f, cursor.getFloat(cursor.getColumnIndexOrThrow("minScoreThreshold")), 0.001f)
        cursor.close()
        db.close()
    }

    @Test
    fun migration15to16_createsSmartCollectionArticleTable() {
        val db = openAtVersion15()
        MIGRATION_15_16.migrate(db)

        // Insert without FK enforcement (bare schema insert, no parent rows needed)
        db.execSQL(
            """
            INSERT INTO smart_collection_article (collectionId, articleId, score, matchedAt)
            VALUES ('col-1', 'art-1', 0.55, 1700000000000)
            """.trimIndent()
        )

        val cursor = db.query("SELECT * FROM smart_collection_article WHERE collectionId = 'col-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("col-1", cursor.getString(cursor.getColumnIndexOrThrow("collectionId")))
        assertEquals("art-1", cursor.getString(cursor.getColumnIndexOrThrow("articleId")))
        assertEquals(0.55f, cursor.getFloat(cursor.getColumnIndexOrThrow("score")), 0.001f)
        cursor.close()
        db.close()
    }

    @Test
    fun migration15to16_createsExpectedIndices() {
        val db = openAtVersion15()
        MIGRATION_15_16.migrate(db)

        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name IN ('smart_collection','smart_collection_article')"
        )
        val indices = buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
        cursor.close()
        db.close()

        assertTrue(indices.any { it.contains("accountId") })
        assertTrue(indices.any { it.contains("collectionId") })
        assertTrue(indices.any { it.contains("articleId") })
    }
}
