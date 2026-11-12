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
class RoomMigration20To21Test {

    private fun openAtVersion20(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
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
    fun migration20to21_createsOracleMarketAndKeepsArticles() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)

        assertEquals(1L, scalarLong(db, "SELECT COUNT(*) FROM article"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_market"))
        assertEquals(
            listOf("id", "articleId", "status", "attempts", "question", "expiresAt", "resolutionCriteria", "createdAt"),
            columns(db, "oracle_market"),
        )
        assertTrue(indices(db, "oracle_market").contains("index_oracle_market_articleId"))
        db.close()
    }

    @Test
    fun migration20to21_isIdempotent() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)
        MIGRATION_20_21.migrate(db)
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_market"))
        db.close()
    }

    @Test
    fun articleIdIsUnique() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)
        db.execSQL("INSERT INTO oracle_market (articleId, status, attempts, createdAt) VALUES ('a1', 'NO_CLAIM', 0, 1)")
        val failed = runCatching {
            db.execSQL("INSERT INTO oracle_market (articleId, status, attempts, createdAt) VALUES ('a1', 'NO_CLAIM', 0, 2)")
        }.isFailure
        assertTrue("a second row for the same article must be refused", failed)
        db.close()
    }

    @Test
    fun deletingTheArticleCascades() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)
        db.execSQL("INSERT INTO oracle_market (articleId, status, attempts, createdAt) VALUES ('a1', 'NO_CLAIM', 0, 1)")
        db.execSQL("DELETE FROM article WHERE id='a1'")
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_market"))
        db.close()
    }

    @Test
    fun migrationTouchesNothingButOracleMarket() {
        val db = openAtVersion20()
        val before = tableNames(db)
        MIGRATION_20_21.migrate(db)
        assertEquals(before + "oracle_market", tableNames(db))
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

    private fun tableNames(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun scalarLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }
}
