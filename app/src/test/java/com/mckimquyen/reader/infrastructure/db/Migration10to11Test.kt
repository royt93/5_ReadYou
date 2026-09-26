package com.mckimquyen.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration10to11Test {

    private fun openV10Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration
            .builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration10to11_createsEmbeddingTableWithTextContentHash() {
        val db = openV10Database()

        MIGRATION_10_11.migrate(db)

        db.execSQL(
            """
            INSERT INTO article_embedding (articleId, contentHash, embedding, updatedAt)
            VALUES ('art_1', 'abc123', '0.1,0.2,0.3', 1700000000000)
            """.trimIndent()
        )
        db.query("SELECT articleId, contentHash, embedding, updatedAt FROM article_embedding").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("art_1", cursor.getString(0))
            assertEquals("abc123", cursor.getString(1))
            assertEquals("0.1,0.2,0.3", cursor.getString(2))
            assertEquals(1700000000000L, cursor.getLong(3))
        }
        db.close()
    }

    @Test
    fun migration10to11_enforcesPrimaryKeyUniqueness() {
        val db = openV10Database()
        MIGRATION_10_11.migrate(db)
        db.execSQL(
            "INSERT INTO article_embedding (articleId, contentHash, embedding, updatedAt) VALUES ('art_1', 'h1', '0.1', 1)"
        )

        db.execSQL(
            "INSERT OR REPLACE INTO article_embedding (articleId, contentHash, embedding, updatedAt) VALUES ('art_1', 'h2', '0.2', 2)"
        )

        db.query("SELECT COUNT(*) FROM article_embedding WHERE articleId = 'art_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT contentHash FROM article_embedding WHERE articleId = 'art_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("h2", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun migration10to11_isIdempotentWhenTableAlreadyExists() {
        val db = openV10Database()
        MIGRATION_10_11.migrate(db)
        db.execSQL(
            "INSERT INTO article_embedding (articleId, contentHash, embedding, updatedAt) VALUES ('art_1', 'h1', '0.1', 1)"
        )

        MIGRATION_10_11.migrate(db)

        db.query("SELECT COUNT(*) FROM article_embedding").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Re-running the migration must not drop data", 1, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration10to11_declaresNoExtraIndexAndKeepsForeignKeyOnArticle() {
        val db = openV10Database()

        MIGRATION_10_11.migrate(db)

        // Room compares indices exactly: the entity declares none beyond the primary key, so any
        // explicit index here would fail startup with "Migration didn't properly handle".
        db.query("PRAGMA index_list(`article_embedding`)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                assertFalse(
                    "Migration must not create an index the entity does not declare",
                    name == "index_article_embedding_articleId",
                )
            }
        }
        db.query("PRAGMA foreign_key_list(`article_embedding`)").use { cursor ->
            assertTrue("Embedding rows must reference article", cursor.moveToFirst())
            assertEquals("article", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
        db.close()
    }

    @Test
    fun migration10to11_leavesOtherTablesUntouched() {
        val db = openV10Database()
        db.execSQL("CREATE TABLE IF NOT EXISTS probe (id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("INSERT INTO probe (id) VALUES ('kept')")

        MIGRATION_10_11.migrate(db)

        db.query("SELECT id FROM probe").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("kept", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
        db.close()
    }
}
