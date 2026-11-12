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
class RoomMigration21To22Test {

    private fun openAtVersion21(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(21) {
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
    fun migration21to22_createsBothTablesAndKeepsArticles() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)

        assertEquals(1L, scalarLong(db, "SELECT COUNT(*) FROM article"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM user_wallet"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_bet"))
        assertEquals(listOf("id", "balance", "lastGrantEpochDay"), columns(db, "user_wallet"))
        assertEquals(
            listOf("id", "articleId", "question", "expiresAt", "side", "stake", "status", "placedAt", "settledAt"),
            columns(db, "oracle_bet"),
        )
        assertTrue(indices(db, "oracle_bet").contains("index_oracle_bet_articleId"))
        db.close()
    }

    @Test
    fun migration21to22_isIdempotent() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)
        MIGRATION_21_22.migrate(db)
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_bet"))
        db.close()
    }

    @Test
    fun oneBetPerArticle() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)
        db.execSQL("INSERT INTO oracle_bet (articleId, question, expiresAt, side, stake, status, placedAt) VALUES ('a1', 'Q?', 9, 'YES', 50, 'OPEN', 1)")
        val failed = runCatching {
            db.execSQL("INSERT INTO oracle_bet (articleId, question, expiresAt, side, stake, status, placedAt) VALUES ('a1', 'Q?', 9, 'NO', 50, 'OPEN', 2)")
        }.isFailure
        assertTrue("a second bet for the same article must be refused", failed)
        db.close()
    }

    @Test
    fun aBetSurvivesTheDeletionOfItsArticle_becauseItHasNoForeignKey() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)
        db.execSQL("INSERT INTO oracle_bet (articleId, question, expiresAt, side, stake, status, placedAt) VALUES ('a1', 'Q?', 9, 'YES', 100, 'OPEN', 1)")
        db.execSQL("DELETE FROM article WHERE id='a1'")
        assertEquals("points staked must not vanish with the article", 1L, scalarLong(db, "SELECT COUNT(*) FROM oracle_bet"))
        assertFalse(foreignKeysOf(db, "oracle_bet").isNotEmpty())
        db.close()
    }

    @Test
    fun migrationTouchesNothingButTheTwoNewTables() {
        val db = openAtVersion21()
        val before = tableNames(db)
        MIGRATION_21_22.migrate(db)
        assertEquals(before + setOf("user_wallet", "oracle_bet"), tableNames(db))
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

    private fun foreignKeysOf(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("table"))) }
        }

    private fun tableNames(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun scalarLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }
}
