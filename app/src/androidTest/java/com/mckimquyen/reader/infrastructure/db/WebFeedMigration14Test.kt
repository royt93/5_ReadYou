package com.mckimquyen.reader.infrastructure.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WebFeedMigration14Test {
    private fun exported(version: Int) =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.mckimquyen.reader.infrastructure.db.AndroidDatabase/$version.json")
            .bufferedReader().use { JsonParser().parse(it.readText()).asJsonObject.getAsJsonObject("database") }

    private fun legacyDb(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext<Context>())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.apply {
            execSQL("CREATE TABLE `group` (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, accountId INTEGER NOT NULL)")
            execSQL("""CREATE TABLE feed (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT, url TEXT NOT NULL,
                groupId TEXT NOT NULL, accountId INTEGER NOT NULL, isNotification INTEGER NOT NULL,
                isFullContent INTEGER NOT NULL,
                FOREIGN KEY(groupId) REFERENCES `group`(id) ON UPDATE CASCADE ON DELETE CASCADE
            )""")
            execSQL("""INSERT INTO `group`(id,name,accountId) VALUES ('g1','News',1)""")
            execSQL("""INSERT INTO feed(id,name,url,groupId,accountId,isNotification,isFullContent)
                VALUES ('rss1','RSS','https://example.test/rss','g1',1,0,0)""")
        }
    }

    @Test
    fun exported14AddsOnlyFeedSourceMetadataAndDefaultKeepsExistingFeedsRss() {
        val schema13 = exported(13).getAsJsonArray("entities").map { it.asJsonObject }.associateBy { it.get("tableName").asString }
        val schema14 = exported(14).getAsJsonArray("entities").map { it.asJsonObject }.associateBy { it.get("tableName").asString }
        val version13 = exported(13)
        val version14 = exported(14)
        val feed13 = schema13.getValue("feed")
        val feed14 = schema14.getValue("feed")

        assertEquals(14, version14.get("version").asInt)
        assertEquals(13, version13.get("version").asInt)
        assertEquals(feed13.getAsJsonArray("fields").size() + 2, feed14.getAsJsonArray("fields").size())
        assertEquals("'RSS'", feed14.getAsJsonArray("fields").map { it.asJsonObject }
            .single { it.get("columnName").asString == "sourceType" }.get("defaultValue").asString)
        assertEquals(schema13.keys, schema14.keys)
        schema13.filterKeys { it != "feed" }.forEach { (table, oldSchema) -> assertEquals(oldSchema, schema14[table]) }
    }

    @Test
    fun migrationAddsDefaultRssMetadataAndWebFeedCanPersistSourceUrl() {
        val db = legacyDb()
        MIGRATION_13_14.migrate(db)
        db.query("SELECT sourceType, sourceUrl FROM feed WHERE id='rss1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("RSS", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        db.execSQL("""INSERT INTO feed(id,name,url,groupId,accountId,isNotification,isFullContent,sourceType,sourceUrl)
            VALUES ('web1','Blog','https://example.test/blog','g1',1,0,0,'WEB','https://example.test/blog')""")
        db.query("SELECT sourceType, sourceUrl FROM feed WHERE id='web1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WEB", cursor.getString(0))
            assertEquals("https://example.test/blog", cursor.getString(1))
        }
        db.close()
    }
}
