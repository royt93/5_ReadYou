package com.mckimquyen.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The migrations 2->3, 3->4, 4->5 and 5->6 run on a real SQLite database that was created from the schema Room exported when that version shipped
 * (the JSON files in src/test/resources/oldschema; versions 2 to 5 come from the git history of the upstream project, 5 and 6 from this app).
 * The old tables are filled with rows first, so a migration that loses or reshapes data is caught, then the whole chain is run and the final shape is
 * compared with the schema Room exports for version 6.
 * Version 1 has no exported schema in the repository, so 1->2 is checked with a hand-built table in [migration1to2_addsTheImageColumn_keepsRows].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MigrationFromOldSchemaTest {
    private val openHelpers = mutableListOf<SupportSQLiteOpenHelper>()

    @After fun closeAll() { openHelpers.forEach { it.close() } }

    private fun schema(name: String): JSONObject {
        val file = listOf("src/test/resources/oldschema/$name", "app/src/test/resources/oldschema/$name").map(::File).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun createSqlOf(db: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val entities = db.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            out += e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) {
                out += idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))
            }
        }
        return out
    }

    private fun open(version: Int, create: List<String>): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) { create.forEach(db::execSQL) }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        openHelpers += helper
        return helper.writableDatabase
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Map<String, Triple<String, Boolean, String?>> {
        val out = linkedMapOf<String, Triple<String, Boolean, String?>>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                out[c.getString(c.getColumnIndexOrThrow("name"))] = Triple(
                    c.getString(c.getColumnIndexOrThrow("type")).uppercase(),
                    c.getInt(c.getColumnIndexOrThrow("notnull")) == 1,
                    c.getString(c.getColumnIndexOrThrow("dflt_value")),
                )
            }
        }
        return out
    }

    private fun tables(db: SupportSQLiteDatabase): Set<String> {
        val out = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' AND name <> 'room_master_table'").use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out
    }

    private fun expectedColumns(db: JSONObject, table: String): Set<String> {
        val entities = db.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            if (e.getString("tableName") == table) {
                val f = e.getJSONArray("fields")
                return (0 until f.length()).map { f.getJSONObject(it).getString("columnName") }.toSet()
            }
        }
        error("no table $table")
    }

    /** Inserts rows using only the columns the given old schema has, giving every NOT NULL column without a default a value. */
    private fun seed(db: SupportSQLiteDatabase, schemaFile: String, withLastArticleId: Boolean, withSyncColumns: Boolean) {
        val s = schema(schemaFile)
        fun has(table: String, column: String) = column in expectedColumns(s, table)
        fun insert(table: String, values: Map<String, Any>) {
            val kept = values.filterKeys { has(table, it) }
            val cols = kept.keys.joinToString(",") { "`$it`" }
            val marks = kept.keys.joinToString(",") { "?" }
            db.execSQL("INSERT INTO `$table` ($cols) VALUES ($marks)", kept.values.toTypedArray())
        }
        insert("account", mapOf("id" to 1, "name" to "Local", "type" to 1, "updateAt" to 1000))
        insert("account", mapOf("id" to 2, "name" to "Second", "type" to 2, "updateAt" to 2000))
        insert("group", mapOf("id" to "g1", "name" to "Default", "accountId" to 1))
        insert("feed", mapOf("id" to "f1", "name" to "Feed", "url" to "https://example.com/rss", "groupId" to "g1", "accountId" to 1, "isNotification" to 0, "isFullContent" to 0))
        insert("article", mapOf(
            "id" to "a1", "date" to 5000, "title" to "First", "rawDescription" to "raw", "shortDescription" to "short", "link" to "https://example.com/1",
            "feedId" to "f1", "accountId" to 1, "isUnread" to 1, "isStarred" to 0, "isReadLater" to 0,
        ))
        insert("article", mapOf(
            "id" to "a2", "date" to 6000, "title" to "Second", "rawDescription" to "raw2", "shortDescription" to "short2", "link" to "https://example.com/2",
            "feedId" to "f1", "accountId" to 1, "isUnread" to 0, "isStarred" to 1, "isReadLater" to 0,
        ))
        if (withSyncColumns) db.execSQL("UPDATE account SET syncInterval = 15 WHERE id = 2")
        if (withLastArticleId) db.execSQL("UPDATE account SET lastArticleId = 'a2' WHERE id = 1")
    }

    private fun rowCounts(db: SupportSQLiteDatabase): Map<String, Int> =
        listOf("account", "feed", "article", "`group`").associateWith { t -> db.query("SELECT COUNT(*) FROM $t").use { it.moveToFirst(); it.getInt(0) } }

    @Test
    fun theSchemaFilesAreTheRealOnes_versionAndTablesAsExpected() {
        for ((file, version) in listOf("ry_2.json" to 2, "ry_3.json" to 3, "ry_4.json" to 4, "ry_5.json" to 5, "cur_5.json" to 5, "cur_6.json" to 6)) {
            assertEquals(file, version, schema(file).getInt("version"))
        }
    }

    @Test
    fun theOldVersion5IsTheSameShapeAsThisAppsVersion5() {
        // The upstream database that became this app's version 5 must be column for column the one this app declares, otherwise a user coming from it is not on the schema the migrations expect.
        for (table in listOf("account", "feed", "article", "group")) {
            assertEquals(table, expectedColumns(schema("cur_5.json"), table), expectedColumns(schema("ry_5.json"), table))
        }
    }

    @Test
    fun chain2to6_keepsEveryRow_andEndsOnTheExportedVersion6Shape() {
        val db = open(2, createSqlOf(schema("ry_2.json")))
        seed(db, "ry_2.json", withLastArticleId = false, withSyncColumns = false)
        val before = rowCounts(db)
        MIGRATION_2_3.migrate(db); MIGRATION_3_4.migrate(db); MIGRATION_4_5.migrate(db); MIGRATION_5_6.migrate(db)
        assertEquals("no row lost or added", before, rowCounts(db))
        val v6 = schema("cur_6.json")
        for (table in listOf("account", "feed", "article", "group")) {
            assertEquals("columns of $table after 2->6", expectedColumns(v6, table), columns(db, table).keys)
        }
        assertTrue("the new table of version 6 exists", "added_rss_source" in tables(db))
        assertEquals(expectedColumns(v6, "added_rss_source"), columns(db, "added_rss_source").keys)
        db.query("SELECT title, isStarred FROM article ORDER BY id").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("First", c.getString(0)); assertEquals(0, c.getInt(1))
            assertTrue(c.moveToNext()); assertEquals("Second", c.getString(0)); assertEquals(1, c.getInt(1))
        }
    }

    @Test
    fun step2to3_addsTheSyncColumnsWithTheirDefaults_andKeepsAccounts() {
        val db = open(2, createSqlOf(schema("ry_2.json")))
        seed(db, "ry_2.json", withLastArticleId = false, withSyncColumns = false)
        MIGRATION_2_3.migrate(db)
        assertEquals(expectedColumns(schema("ry_3.json"), "account"), columns(db, "account").keys)
        assertEquals(expectedColumns(schema("ry_3.json"), "article"), columns(db, "article").keys)
        db.query("SELECT syncInterval, syncBlockList FROM account WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(30, c.getInt(0)); assertEquals("", c.getString(1))
        }
    }

    @Test
    fun step3to4_addsTheSecurityKeyColumn() {
        val db = open(3, createSqlOf(schema("ry_3.json")))
        seed(db, "ry_3.json", withLastArticleId = false, withSyncColumns = true)
        MIGRATION_3_4.migrate(db)
        assertEquals(expectedColumns(schema("ry_4.json"), "account"), columns(db, "account").keys)
        db.query("SELECT syncInterval FROM account WHERE id = 2").use { c -> assertTrue(c.moveToFirst()); assertEquals("the value set before is kept", 15, c.getInt(0)) }
    }

    @Test
    fun step4to5_addsLastArticleId_nullForExistingAccounts() {
        val db = open(4, createSqlOf(schema("ry_4.json")))
        seed(db, "ry_4.json", withLastArticleId = false, withSyncColumns = true)
        MIGRATION_4_5.migrate(db)
        assertEquals(expectedColumns(schema("ry_5.json"), "account"), columns(db, "account").keys)
        db.query("SELECT lastArticleId FROM account WHERE id = 1").use { c -> assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)) }
    }

    @Test
    fun step5to6_createsTheRssSourceTable_andTheIndex_withoutTouchingTheRest() {
        val db = open(5, createSqlOf(schema("ry_5.json")))
        seed(db, "ry_5.json", withLastArticleId = true, withSyncColumns = true)
        val before = rowCounts(db)
        MIGRATION_5_6.migrate(db)
        assertEquals(before, rowCounts(db))
        assertTrue("added_rss_source" in tables(db))
        db.execSQL("INSERT INTO added_rss_source (url, name, addedAt, accountId) VALUES ('https://example.com/rss', 'Ex', 1, 1)")
        db.query("SELECT name FROM added_rss_source WHERE accountId = 1").use { c -> assertTrue(c.moveToFirst()); assertEquals("Ex", c.getString(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_added_rss_source_accountId'").use { c -> assertTrue(c.moveToFirst()) }
        db.query("SELECT lastArticleId FROM account WHERE id = 1").use { c -> assertTrue(c.moveToFirst()); assertEquals("a2", c.getString(0)) }
    }

    @Test
    fun migration1to2_addsTheImageColumn_keepsRows() {
        // Version 1 was never exported as a schema file; the table is built by hand with the columns this migration depends on.
        val db = open(1, listOf("CREATE TABLE article (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)"))
        db.execSQL("INSERT INTO article (id, title) VALUES ('a1', 'Old row')")
        MIGRATION_1_2.migrate(db)
        assertTrue("img" in columns(db, "article").keys)
        db.query("SELECT title, img FROM article").use { c -> assertTrue(c.moveToFirst()); assertEquals("Old row", c.getString(0)); assertTrue(c.isNull(1)) }
    }
}
