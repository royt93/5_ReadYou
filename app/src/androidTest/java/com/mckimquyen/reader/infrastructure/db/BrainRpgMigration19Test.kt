package com.mckimquyen.reader.infrastructure.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrainRpgMigration19Test {

    private fun exportedV18Entities(): List<com.google.gson.JsonObject> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.mckimquyen.reader.infrastructure.db.AndroidDatabase/18.json")
            .bufferedReader().use { it.readText() }
        val database = JsonParser().parse(json).asJsonObject.getAsJsonObject("database")
        return database.getAsJsonArray("entities").map { it.asJsonObject }
    }

    @Test
    fun databaseOpen_withAllMigrations_upgradesVersion18ThroughLatest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath(TEST_DB_NAME).absolutePath).also {
            it.parentFile?.mkdirs()
            it.delete()
        }
        val entities = exportedV18Entities()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        entities.forEach { entity ->
                            val tableName = entity.get("tableName").asString
                            val createSql = entity.get("createSql").asString
                                .replace("\${TABLE_NAME}", tableName)
                            db.execSQL(createSql)
                            entity.getAsJsonArray("indices")?.forEach { index ->
                                index.asJsonObject.get("createSql")?.asString
                                    ?.replace("\${TABLE_NAME}", tableName)
                                    ?.let(db::execSQL)
                            }
                            entity.getAsJsonArray("contentSyncTriggers")?.forEach { trigger -> db.execSQL(trigger.asString) }
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val seed = helper.writableDatabase
            seed.execSQL("INSERT INTO brain_rpg_profile VALUES (1, 1025, 6, 0, 0, 0, 0, 1)")
            seed.execSQL("INSERT INTO brain_rpg_category VALUES ('Science & Nature', 400, 1000, 0)")
            seed.execSQL("INSERT INTO brain_rpg_reward (articleId, rewardType, createdAtMillis) VALUES ('a1', 'READ', 1000)")
            helper.close()

            val database = androidx.room.Room.databaseBuilder(context, AndroidDatabase::class.java, dbFile.name)
                .addMigrations(*allMigrations)
                .build()
            try {
                val profile = runBlocking { database.brainRpgDao().getProfile() }
                val rewards = runBlocking { database.brainRpgDao().allRewards() }
                val events = runBlocking { database.brainRpgDao().allXpEvents() }
                assertEquals(LATEST_DB_VERSION, database.openHelper.writableDatabase.version)
                assertEquals(1025L, profile?.baseTotalXp)
                assertEquals(6, profile?.streakDays)
                assertEquals(1, rewards.size)
                assertTrue(events.isEmpty())
            } finally {
                database.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbFile.name)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "brain_rpg_migration_19_test.db"
        const val LATEST_DB_VERSION = 20
    }
}
