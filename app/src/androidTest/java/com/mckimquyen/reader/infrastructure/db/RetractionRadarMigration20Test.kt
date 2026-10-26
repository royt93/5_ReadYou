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
import com.mckimquyen.reader.domain.model.radar.ArticleSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Builds a real v19 database from the exported schema, then opens it with the production migration chain. */
@RunWith(AndroidJUnit4::class)
class RetractionRadarMigration20Test {

    private fun exportedV19Entities(): List<com.google.gson.JsonObject> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.mckimquyen.reader.infrastructure.db.AndroidDatabase/19.json")
            .bufferedReader().use { it.readText() }
        return JsonParser().parse(json).asJsonObject.getAsJsonObject("database")
            .getAsJsonArray("entities").map { it.asJsonObject }
    }

    @Test
    fun databaseOpen_withAllMigrations_upgrades19To20_keepsArticlesAndEnablesSnapshots() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath(TEST_DB_NAME).absolutePath).also {
            it.parentFile?.mkdirs()
            it.delete()
        }
        val entities = exportedV19Entities()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        entities.forEach { entity ->
                            val tableName = entity.get("tableName").asString
                            db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                            entity.getAsJsonArray("indices")?.forEach { index ->
                                index.asJsonObject.get("createSql")?.asString
                                    ?.replace("\${TABLE_NAME}", tableName)?.let(db::execSQL)
                            }
                            entity.getAsJsonArray("contentSyncTriggers")?.forEach { db.execSQL(it.asString) }
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            helper.writableDatabase.let { seed ->
                seed.execSQL("INSERT INTO account (id, name, type, syncInterval, syncOnStart, syncOnlyOnWiFi, syncOnlyWhenCharging, keepArchived, syncBlockList) VALUES (1, 'A', 1, 0, 0, 0, 0, 0, '')")
                seed.execSQL("INSERT INTO `group` (id, name, accountId) VALUES ('g', 'G', 1)")
                seed.execSQL("INSERT INTO feed (id, name, url, groupId, accountId, isNotification, isFullContent, sourceType) VALUES ('f', 'F', 'https://e.com', 'g', 1, 0, 0, 'RSS')")
                seed.execSQL("INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId, isUnread, isStarred, isReadLater) VALUES ('a1', 1000, 'Existing', 'raw', 'short', 'https://e.com/a1', 'f', 1, 1, 0, 0)")
            }
            helper.close()

            val database = Room.databaseBuilder(context, AndroidDatabase::class.java, dbFile.name)
                .addMigrations(*allMigrations).build()
            try {
                val dao = database.articleRadarDao()
                runBlocking {
                    dao.insertSnapshot(ArticleSnapshot(articleId = "a1", capturedAt = 1, titleHash = "t", contentHash = "c", title = "Existing", contentText = "raw"))
                }
                assertEquals(LATEST_DB_VERSION, database.openHelper.writableDatabase.version)
                assertEquals("Existing", database.openHelper.readableDatabase
                    .query("SELECT title FROM article WHERE id='a1'").use { it.moveToFirst(); it.getString(0) })
                assertNotNull(runBlocking { dao.firstSnapshot("a1") })
                assertEquals(1, runBlocking { dao.snapshotCount("a1") })
            } finally {
                database.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbFile.name)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "radar_migration_20_test.db"
        const val LATEST_DB_VERSION = 20
    }
}
