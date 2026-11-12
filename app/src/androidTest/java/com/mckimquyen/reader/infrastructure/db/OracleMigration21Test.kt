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
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Builds a real v20 database from the exported schema, then opens it with every migration through Room (ORACLE-01). */
@RunWith(AndroidJUnit4::class)
class OracleMigration21Test {

    private fun exportedV20Entities(): List<com.google.gson.JsonObject> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.mckimquyen.reader.infrastructure.db.AndroidDatabase/20.json")
            .bufferedReader().use { it.readText() }
        val database = JsonParser().parse(json).asJsonObject.getAsJsonObject("database")
        return database.getAsJsonArray("entities").map { it.asJsonObject }
    }

    private fun createV20(context: Context, dbFile: File): SupportSQLiteOpenHelper {
        val entities = exportedV20Entities()
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(OLD_VERSION) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        entities.forEach { entity ->
                            val tableName = entity.get("tableName").asString
                            db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                            entity.getAsJsonArray("indices")?.forEach { index ->
                                index.asJsonObject.get("createSql")?.asString
                                    ?.replace("\${TABLE_NAME}", tableName)?.let(db::execSQL)
                            }
                            entity.getAsJsonArray("contentSyncTriggers")?.forEach { trigger -> db.execSQL(trigger.asString) }
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
    }

    private fun freshFile(context: Context): File = File(context.getDatabasePath(TEST_DB_NAME).absolutePath).also {
        it.parentFile?.mkdirs()
        it.delete()
    }

    @Test
    fun upgradeFromV20_keepsOldDataAndCreatesOracleMarket() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = freshFile(context)
        val helper = createV20(context, dbFile)
        try {
            val seed = helper.writableDatabase
            seed.execSQL("INSERT INTO brain_rpg_profile VALUES (1, 1025, 6, 0, 0, 0, 0, 1)")
            seed.execSQL("INSERT INTO brain_rpg_category VALUES ('Science & Nature', 400, 1000, 0)")
            helper.close()

            val database = Room.databaseBuilder(context, AndroidDatabase::class.java, dbFile.name)
                .addMigrations(*allMigrations).build()
            try {
                assertEquals(LATEST_VERSION, database.openHelper.writableDatabase.version)
                val profile = runBlocking { database.brainRpgDao().getProfile() }
                assertEquals(1025L, profile?.baseTotalXp)
                assertEquals(6, profile?.streakDays)
                database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM oracle_market").use {
                    it.moveToFirst()
                    assertEquals(0, it.getInt(0))
                }
            } finally {
                database.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbFile.name)
        }
    }

    @Test
    fun claimOfADeletedArticleCascadesAway() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = freshFile(context)
        createV20(context, dbFile).close()
        val database = Room.databaseBuilder(context, AndroidDatabase::class.java, dbFile.name)
            .addMigrations(*allMigrations).build()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO account (id, name, type) VALUES (1, 'A', 1)")
            sql.execSQL("INSERT INTO `group` (id, name, accountId) VALUES ('g', 'G', 1)")
            sql.execSQL("INSERT INTO feed (id, name, url, groupId, accountId, isNotification, isFullContent) VALUES ('f', 'F', 'https://e.com/f', 'g', 1, 0, 0)")
            sql.execSQL("INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId, isUnread, isStarred, isReadLater) VALUES ('a1', 1, 'T', 'R', 'S', 'https://e.com/a1', 'f', 1, 1, 0, 0)")
            val dao = database.oracleMarketDao()
            runBlocking {
                dao.upsert(OracleMarket(articleId = "a1", status = OracleMarketStatus.HAS_CLAIM, question = "Q?", expiresAt = 9L, resolutionCriteria = "c", createdAt = 1L))
                assertNotNull(dao.byArticle("a1"))
                sql.execSQL("DELETE FROM article WHERE id='a1'")
                assertNull(dao.byArticle("a1"))
            }
        } finally {
            database.close()
            context.deleteDatabase(dbFile.name)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "oracle_migration_21_test.db"
        const val OLD_VERSION = 20
        const val LATEST_VERSION = 21
    }
}
