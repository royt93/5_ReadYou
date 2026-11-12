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
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.repository.PlaceBetResult
import com.mckimquyen.reader.domain.repository.SettleResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Builds a real v21 database from the exported schema, then opens it with every migration through Room (ORACLE-02). */
@RunWith(AndroidJUnit4::class)
class OracleBettingMigration22Test {

    private fun exportedV21Entities(): List<com.google.gson.JsonObject> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.mckimquyen.reader.infrastructure.db.AndroidDatabase/21.json")
            .bufferedReader().use { it.readText() }
        val database = JsonParser().parse(json).asJsonObject.getAsJsonObject("database")
        return database.getAsJsonArray("entities").map { it.asJsonObject }
    }

    private fun createV21(context: Context, dbFile: File): SupportSQLiteOpenHelper {
        val entities = exportedV21Entities()
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
    fun upgradeFromV21_keepsOldDataAndCreatesTheTwoTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = freshFile(context)
        val helper = createV21(context, dbFile)
        try {
            val seed = helper.writableDatabase
            seed.execSQL("INSERT INTO brain_rpg_profile VALUES (1, 1025, 6, 0, 0, 0, 0, 1)")
            helper.close()

            val database = Room.databaseBuilder(context, AndroidDatabase::class.java, dbFile.name)
                .addMigrations(*allMigrations).build()
            try {
                assertEquals(LATEST_VERSION, database.openHelper.writableDatabase.version)
                assertEquals(1025L, runBlocking { database.brainRpgDao().getProfile() }?.baseTotalXp)
                listOf("user_wallet", "oracle_bet").forEach { table ->
                    database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use {
                        it.moveToFirst()
                        assertEquals(table, 0, it.getInt(0))
                    }
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
    fun aBetSurvivesItsArticleBeingDeleted_andIsStillSettledOnTheRealDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = freshFile(context)
        createV21(context, dbFile).close()
        val database = Room.databaseBuilder(context, AndroidDatabase::class.java, dbFile.name)
            .addMigrations(*allMigrations).build()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO account (id, name, type) VALUES (1, 'A', 1)")
            sql.execSQL("INSERT INTO `group` (id, name, accountId) VALUES ('g', 'G', 1)")
            sql.execSQL("INSERT INTO feed (id, name, url, groupId, accountId, isNotification, isFullContent) VALUES ('f', 'F', 'https://e.com/f', 'g', 1, 0, 0)")
            sql.execSQL("INSERT INTO article (id, date, title, rawDescription, shortDescription, link, feedId, accountId, isUnread, isStarred, isReadLater) VALUES ('a1', 1, 'T', 'R', 'S', 'https://e.com/a1', 'f', 1, 1, 0, 0)")
            val dao = database.oracleWalletDao()
            val day = 86_400_000L
            val now = 100 * day
            runBlocking {
                val placed = dao.placeBet("a1", "Q?", now + day, OracleBetSide.YES, 100, now)
                assertTrue(placed is PlaceBetResult.Placed)
                sql.execSQL("DELETE FROM article WHERE id='a1'")
                assertNotNull("points staked must not vanish with the article", dao.betFor("a1"))
                assertEquals(900L, dao.wallet()?.balance)
                val settled = dao.settleBet("a1", OracleOutcome.YES, now + day)
                assertTrue(settled is SettleResult.Settled)
                assertEquals(1100L, dao.wallet()?.balance)
            }
        } finally {
            database.close()
            context.deleteDatabase(dbFile.name)
        }
    }

    @Test
    fun anUnsettledBetIsGivenBackAfter30Days_onTheRealDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = freshFile(context)
        createV21(context, dbFile).close()
        val database = Room.databaseBuilder(context, AndroidDatabase::class.java, dbFile.name)
            .addMigrations(*allMigrations).build()
        try {
            val dao = database.oracleWalletDao()
            val day = 86_400_000L
            val now = 100 * day
            runBlocking {
                dao.placeBet("a1", "Q?", now + day, OracleBetSide.NO, 500, now)
                assertEquals(500L, dao.wallet()?.balance)
                assertEquals(0, dao.autoRefundStale(now + day + 30 * day - 1))
                assertEquals(1, dao.autoRefundStale(now + day + 30 * day))
                assertEquals(1000L, dao.wallet()?.balance)
            }
        } finally {
            database.close()
            context.deleteDatabase(dbFile.name)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "oracle_betting_migration_22_test.db"
        const val OLD_VERSION = 21
        const val LATEST_VERSION = 22
    }
}
