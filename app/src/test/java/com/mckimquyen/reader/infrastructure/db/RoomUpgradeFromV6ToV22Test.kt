package com.mckimquyen.reader.infrastructure.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The real upgrade path of a user whose phone holds database version 6: the file is built from the schema Room exported for version 6, filled with rows,
 * and then opened by the production `Room.databaseBuilder(...).addMigrations(*allMigrations)`. Room runs every migration 6->22 and then validates
 * the result against the version 22 schema it was compiled with; a mismatch throws IllegalStateException on first use, which is a crash for every
 * old user. Room's own MigrationTestHelper cannot be used in this project (it needs kotlinx-serialization 1.8, the app is pinned to 1.7.3), so
 * the opening itself is the check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomUpgradeFromV6ToV22Test {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "upgrade-from-v6-test"
    private var db: AndroidDatabase? = null

    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(name)
    }

    private fun v6Schema(): JSONObject {
        val file = listOf(
            "schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/6.json",
            "app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/6.json",
        ).map(::File).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** Creates the file `name` at version 6 exactly as Room would have created it, including the room_master_table identity row. */
    private fun createVersion6File(): SupportSQLiteDatabase {
        val schema = v6Schema()
        val sqls = mutableListOf<String>()
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            sqls += e.getString("createSql").replace("\${TABLE_NAME}", table)
            e.optJSONArray("indices")?.let { idx -> for (j in 0 until idx.length()) sqls += idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table) }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqls += setup.getString(i)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) { sqls.forEach(db::execSQL) }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun seed(old: SupportSQLiteDatabase) {
        old.execSQL("INSERT INTO account (id, name, type, updateAt, syncInterval) VALUES (1, 'Local', 1, 1000, 15)")
        old.execSQL("INSERT INTO `group` (id, name, accountId) VALUES ('g1', 'Default', 1)")
        old.execSQL("INSERT INTO feed (id, name, url, groupId, accountId, isNotification, isFullContent) VALUES ('f1', 'Feed', 'https://example.com/rss', 'g1', 1, 0, 0)")
        old.execSQL(
            "INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId, isUnread, isStarred, isReadLater) " +
                "VALUES ('a1', 5000, 'Read me', 'raw', 'short', 'https://example.com/1', 'f1', 1, 1, 0, 0)",
        )
        old.execSQL(
            "INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId, isUnread, isStarred, isReadLater) " +
                "VALUES ('a2', 6000, 'Starred', 'raw2', 'short2', 'https://example.com/2', 'f1', 1, 0, 1, 0)",
        )
        old.execSQL("INSERT INTO added_rss_source (url, name, addedAt, accountId) VALUES ('https://example.com/rss', 'Ex', 1, 1)")
    }

    @Test
    fun aVersion6Database_opensWithTheProductionMigrations_andRoomAcceptsTheVersion22Schema() {
        val old = createVersion6File()
        seed(old)
        old.close()

        val upgraded = Room.databaseBuilder(context, AndroidDatabase::class.java, name)
            .addMigrations(*allMigrations)
            .allowMainThreadQueries()
            .build()
        db = upgraded
        // Opening is lazy: the first statement makes Room migrate and then validate against the version 22 schema.
        val opened = upgraded.openHelper.writableDatabase
        assertEquals(22, opened.version)
        // Reads through the compiled DAO queries prove the migrated tables match what the code selects.
        runBlocking {
            assertEquals("one unread article of the seeded two", 1, upgraded.articleDao().queryTotalUnreadCount(1))
            assertNotNull("the account is readable through its DAO", upgraded.accountDao().queryById(1))
            assertEquals(1, upgraded.accountDao().queryAll().size)
        }
    }

    @Test
    fun theUpgradeKeepsTheUsersRows() {
        val old = createVersion6File()
        seed(old)
        old.close()
        val upgraded = Room.databaseBuilder(context, AndroidDatabase::class.java, name).addMigrations(*allMigrations).allowMainThreadQueries().build()
        db = upgraded
        val d = upgraded.openHelper.writableDatabase
        fun one(sql: String) = d.query(sql).use { c -> assertTrue(c.moveToFirst()); c.getInt(0) }
        assertEquals(1, one("SELECT COUNT(*) FROM account"))
        assertEquals(1, one("SELECT COUNT(*) FROM feed"))
        assertEquals(2, one("SELECT COUNT(*) FROM article"))
        assertEquals("the starred article stays starred", 1, one("SELECT isStarred FROM article WHERE id = 'a2'"))
        assertEquals("the unread article stays unread", 1, one("SELECT isUnread FROM article WHERE id = 'a1'"))
        assertEquals("the user's added RSS source stays", 1, one("SELECT COUNT(*) FROM added_rss_source"))
        assertEquals("a sync setting set before is kept", 15, one("SELECT syncInterval FROM account WHERE id = 1"))
    }

    @Test
    fun theProductionBuilderDoesNotFallBackToWipingTheData() {
        // Without a migration path Room would throw; it must never silently drop the tables. The source must not ask for a destructive fallback.
        val src = listOf("src/main/java", "app/src/main/java").map(::File).first { it.exists() }
            .walkTopDown().filter { it.extension == "kt" }.filter { Regex("""fallbackToDestructiveMigration""").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals("a destructive fallback would wipe user data on a missing migration: $src", emptyList<String>(), src)
    }
}
