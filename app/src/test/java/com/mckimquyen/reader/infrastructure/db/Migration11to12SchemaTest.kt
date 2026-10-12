package com.mckimquyen.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Checks that what MIGRATION_11_12 builds matches the schema Room exported for version 12 (12.json), which is
 * what Room validates at runtime. The official MigrationTestHelper cannot be used here (room-testing 2.8
 * needs kotlinx-serialization >= 1.8 while the app is pinned to 1.7.3), so the exported JSON is compared with
 * PRAGMA table_info / index_list of a database that was actually migrated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration11to12SchemaTest {

    private fun schema(version: Int): JsonObject {
        val file = listOf("schemas", "app/schemas")
            .map { File(it, "com.mckimquyen.reader.infrastructure.db.AndroidDatabase/$version.json") }
            .firstOrNull(File::exists) ?: error("exported schema $version.json not found from ${File(".").absolutePath}")
        return JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("database")
    }

    private fun entity(db: JsonObject, table: String): JsonObject =
        db.getAsJsonArray("entities").map { it.asJsonObject }.single { it.get("tableName").asString == table }

    private fun migratedDb(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.also { MIGRATION_11_12.migrate(it) }
    }

    private data class Column(val name: String, val type: String, val notNull: Boolean, val pk: Int)

    private fun columns(db: SupportSQLiteDatabase, table: String): List<Column> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Column(
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            type = c.getString(c.getColumnIndexOrThrow("type")).uppercase(),
                            notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1,
                            pk = c.getInt(c.getColumnIndexOrThrow("pk")),
                        )
                    )
                }
            }
        }

    @Test
    fun schema12_declaresDailyDigest_whichSchema11DidNot() {
        val tables11 = schema(11).getAsJsonArray("entities").map { it.asJsonObject.get("tableName").asString }
        val tables12 = schema(12).getAsJsonArray("entities").map { it.asJsonObject.get("tableName").asString }
        assertFalse("daily_digest" in tables11)
        assertTrue("daily_digest" in tables12)
        assertEquals("only daily_digest is new in 12", setOf("daily_digest"), tables12.toSet() - tables11.toSet())
    }

    @Test
    fun migratedColumns_matchTheExportedSchema_nameTypeAndNullability() {
        val expected = entity(schema(12), "daily_digest").getAsJsonArray("fields").map {
            val f = it.asJsonObject
            f.get("columnName").asString to (f.get("affinity").asString to f.get("notNull").asBoolean)
        }
        val db = migratedDb()
        val actual = columns(db, "daily_digest").map { it.name to (it.type to it.notNull) }
        db.close()

        assertEquals(expected.toSet(), actual.toSet())
        assertEquals("no extra or missing columns", expected.size, actual.size)
    }

    @Test
    fun migratedPrimaryKey_matchesTheExportedSchema() {
        val expectedPk = entity(schema(12), "daily_digest").getAsJsonObject("primaryKey")
            .getAsJsonArray("columnNames").map { it.asString }
        val db = migratedDb()
        val actualPk = columns(db, "daily_digest").filter { it.pk > 0 }.sortedBy { it.pk }.map { it.name }
        db.close()
        assertEquals(expectedPk, actualPk)
    }

    @Test
    fun migratedIndices_matchTheExportedSchema_nameUniquenessAndColumns() {
        val expected = entity(schema(12), "daily_digest").getAsJsonArray("indices").map {
            val i = it.asJsonObject
            Triple(i.get("name").asString, i.get("unique").asBoolean, i.getAsJsonArray("columnNames").map { c -> c.asString })
        }
        val db = migratedDb()
        val actual = db.query("PRAGMA index_list(`daily_digest`)").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow("name"))
                    if (name.startsWith("sqlite_autoindex")) continue // the implicit PRIMARY KEY index
                    val unique = c.getInt(c.getColumnIndexOrThrow("unique")) == 1
                    val cols = db.query("PRAGMA index_info(`$name`)").use { ic ->
                        buildList { while (ic.moveToNext()) add(ic.getString(ic.getColumnIndexOrThrow("name"))) }
                    }
                    add(Triple(name, unique, cols))
                }
            }
        }
        db.close()
        assertEquals(expected, actual)
    }

    @Test
    fun theMigrationOnlyTouchesDailyDigest_everyOtherExportedTableIsUnchangedBetween11And12() {
        val a = schema(11).getAsJsonArray("entities").map { it.asJsonObject }.associateBy { it.get("tableName").asString }
        val b = schema(12).getAsJsonArray("entities").map { it.asJsonObject }.associateBy { it.get("tableName").asString }
        for ((table, old) in a) {
            assertEquals("table `$table` must be identical in 11 and 12", old, b.getValue(table))
        }
    }

    @Test
    fun theExportedVersionNumbers_matchTheirFileNames() {
        assertEquals(11, schema(11).get("version").asInt)
        assertEquals(12, schema(12).get("version").asInt)
    }
}
