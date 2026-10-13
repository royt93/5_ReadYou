package com.mckimquyen.reader.infrastructure.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/** Version-12 -> 13 schema validation using the exported JSON (room-testing has a serialization ABI conflict). */
@RunWith(AndroidJUnit4::class)
class BrainRpgMigration13Test {
    private fun exported(version: Int): JsonObject {
        val path = "com.mckimquyen.reader.infrastructure.db.AndroidDatabase/$version.json"
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open(path).bufferedReader().use { it.readText() }
        return JsonParser().parse(json).asJsonObject.getAsJsonObject("database")
    }

    private fun migratedDb(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext<Context>())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.also { MIGRATION_12_13.migrate(it) }
    }

    @Test
    fun version13AddsExactlyThreeRpgTables_andVersionNumbersMatch() {
        val tables12 = exported(12).getAsJsonArray("entities").map { it.asJsonObject.get("tableName").asString }.toSet()
        val tables13 = exported(13).getAsJsonArray("entities").map { it.asJsonObject.get("tableName").asString }.toSet()
        val added = tables13 - tables12
        assertEquals(setOf("brain_rpg_profile", "brain_rpg_category", "brain_rpg_reward"), added)
        assertEquals(12, exported(12).get("version").asInt)
        assertEquals(13, exported(13).get("version").asInt)
    }

    @Test
    fun everyNewTableColumnPrimaryKeyAndIndexMatchesExported13Json() {
        val expectedTables = listOf("brain_rpg_profile", "brain_rpg_category", "brain_rpg_reward")
        val schema = exported(13)
        val db = migratedDb()
        for (table in expectedTables) {
            val entity = schema.getAsJsonArray("entities").map { it.asJsonObject }
                .single { it.get("tableName").asString == table }
            val expectedColumns = entity.getAsJsonArray("fields").map { field ->
                val f = field.asJsonObject
                listOf(f.get("columnName").asString, f.get("affinity").asString.uppercase(), f.get("notNull").asBoolean.toString())
            }.toSet()
            val actualColumns = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(listOf(
                            cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            cursor.getString(cursor.getColumnIndexOrThrow("type")).uppercase(),
                            (cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 1).toString(),
                        ))
                    }
                }
            }
            assertEquals("columns for $table", expectedColumns, actualColumns)
            assertEquals("column count for $table", expectedColumns.size, actualColumns.size)

            val expectedPk = entity.getAsJsonObject("primaryKey").getAsJsonArray("columnNames").map { it.asString }
            val actualPk = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val position = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                        if (position > 0) add(position to cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }.sortedBy { it.first }.map { it.second }
            }
            assertEquals("primary key for $table", expectedPk, actualPk)

            val expectedIndices = entity.getAsJsonArray("indices")?.map { it.asJsonObject }?.map { index ->
                Triple(index.get("name").asString, index.get("unique").asBoolean,
                    index.getAsJsonArray("columnNames").map { it.asString })
            }?.toSet().orEmpty()
            val actualIndices = db.query("PRAGMA index_list(`$table`)").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        if (name.startsWith("sqlite_autoindex")) continue
                        val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                        val columns = db.query("PRAGMA index_info(`$name`)").use { indexCursor ->
                            buildList {
                                while (indexCursor.moveToNext()) add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
                            }
                        }
                        add(Triple(name, unique, columns))
                    }
                }
            }
            assertEquals("indices for $table", expectedIndices, actualIndices)
        }
        db.close()
    }

    @Test
    fun migrationPreservesAllPreexistingTables() {
        val schema12 = exported(12).getAsJsonArray("entities").map { it.asJsonObject }.associateBy { it.get("tableName").asString }
        val schema13 = exported(13).getAsJsonArray("entities").map { it.asJsonObject }.associateBy { it.get("tableName").asString }
        schema12.forEach { (table, oldSchema) -> assertEquals("old table $table changed", oldSchema, schema13[table]) }
    }

    @Test
    fun migrationCreatesEmptyTablesAndUniqueRewardKey() {
        val db = migratedDb()
        listOf("brain_rpg_profile", "brain_rpg_category", "brain_rpg_reward").forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
        db.execSQL("INSERT INTO brain_rpg_reward(articleId,rewardType,createdAtMillis) VALUES ('a1','READ',1)")
        var duplicateRejected = false
        try {
            db.execSQL("INSERT INTO brain_rpg_reward(articleId,rewardType,createdAtMillis) VALUES ('a1','READ',2)")
        } catch (_: Exception) {
            duplicateRejected = true
        }
        assertTrue("same article/reward type must be unique", duplicateRejected)
        db.execSQL("INSERT INTO brain_rpg_reward(articleId,rewardType,createdAtMillis) VALUES ('a1','QUIZ',3)")
        db.close()
    }

    @Test
    fun migrationCanRunTwiceWithoutDroppingRows() {
        val db = migratedDb()
        db.execSQL("INSERT INTO brain_rpg_profile(id,baseTotalXp,streakDays,lastReadEpochDay,streakShieldActive,quizzesAttempted,quizzesPassed,legacyImportComplete) VALUES (1,20,1,0,0,0,0,0)")
        MIGRATION_12_13.migrate(db)
        db.query("SELECT baseTotalXp FROM brain_rpg_profile WHERE id=1").use {
            assertTrue(it.moveToFirst())
            assertEquals(20L, it.getLong(0))
        }
        db.close()
    }
}
