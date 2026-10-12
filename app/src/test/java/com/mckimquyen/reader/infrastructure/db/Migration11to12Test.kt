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
class Migration11to12Test {
    @Test
    fun migration11to12_createsDigestHistoryTableAndAccountIndex() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase

        MIGRATION_11_12.migrate(db)
        db.execSQL("INSERT INTO daily_digest VALUES ('d1', 42, 'Brief', '{}', '[\"a1\"]', 1)")

        db.query("SELECT headline, sourceArticleIdsJson, accountId FROM daily_digest WHERE id = 'd1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Brief", cursor.getString(0))
            assertEquals("[\"a1\"]", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.query("PRAGMA index_list(`daily_digest`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("index_daily_digest_accountId_createdAt", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        db.close()
    }
}
