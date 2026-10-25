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
class RoomMigration18To19Test {

    private fun openAtVersion18(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE brain_rpg_profile (id INTEGER NOT NULL PRIMARY KEY, baseTotalXp INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE brain_rpg_category (categoryId TEXT NOT NULL PRIMARY KEY, baseXp INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE brain_rpg_reward (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, articleId TEXT NOT NULL, rewardType TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_brain_rpg_reward_articleId_rewardType ON brain_rpg_reward(articleId, rewardType)")
                    db.execSQL("INSERT INTO brain_rpg_profile (id, baseTotalXp) VALUES (1, 900)")
                    db.execSQL("INSERT INTO brain_rpg_category (categoryId, baseXp) VALUES ('Tech & AI', 400)")
                    db.execSQL("INSERT INTO brain_rpg_reward (articleId, rewardType, createdAtMillis) VALUES ('legacy', 'READ', 1000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration18to19_createsEmptyXpEventsAndPreservesExistingRpgData() {
        val db = openAtVersion18()
        MIGRATION_18_19.migrate(db)

        assertEquals(900L, scalarLong(db, "SELECT baseTotalXp FROM brain_rpg_profile WHERE id=1"))
        assertEquals(400L, scalarLong(db, "SELECT baseXp FROM brain_rpg_category WHERE categoryId='Tech & AI'"))
        assertEquals("READ", scalarText(db, "SELECT rewardType FROM brain_rpg_reward WHERE articleId='legacy'"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM brain_rpg_xp_event"))

        val columns = db.query("PRAGMA table_info(`brain_rpg_xp_event`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertEquals(listOf("id", "categoryId", "xpDelta", "rewardType", "createdAtMillis"), columns)

        val indices = db.query("PRAGMA index_list(`brain_rpg_xp_event`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertTrue(indices.contains("index_brain_rpg_xp_event_createdAtMillis"))
        assertTrue(indices.contains("index_brain_rpg_xp_event_categoryId_createdAtMillis"))
        db.close()
    }

    private fun scalarLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun scalarText(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
}
