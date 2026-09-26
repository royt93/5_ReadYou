package com.mckimquyen.reader.infrastructure.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountTypeConverters
import com.mckimquyen.reader.domain.model.account.KeepArchivedConverters
import com.mckimquyen.reader.domain.model.account.SyncBlockListConverters
import com.mckimquyen.reader.domain.model.account.SyncIntervalConverters
import com.mckimquyen.reader.domain.model.account.SyncOnStartConverters
import com.mckimquyen.reader.domain.model.account.SyncOnlyOnWiFiConverters
import com.mckimquyen.reader.domain.model.account.SyncOnlyWhenChargingConverters
import com.mckimquyen.reader.domain.model.account.sec.DESUtils
import com.mckimquyen.reader.domain.model.addedsource.AddedRssSource
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleFts
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedErrorTypeConverters
import com.mckimquyen.reader.domain.model.feed.FeedHealthRecord
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.repository.ArticleHighlightDao
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.pref.KeepArchivedPreference
import com.mckimquyen.reader.infrastructure.pref.SyncIntervalPref
import com.mckimquyen.reader.infrastructure.pref.SyncOnStartPref
import com.mckimquyen.reader.infrastructure.pref.SyncOnlyOnWiFiPref
import com.mckimquyen.reader.infrastructure.pref.SyncOnlyWhenChargingPref
import com.mckimquyen.reader.ui.ext.toInt
import java.util.Date

@Database(
    entities = [
        Account::class,
        Feed::class,
        Article::class,
        Group::class,
        AddedRssSource::class,
        ArticleHighlightNote::class,
        ArticleFts::class,
        FeedHealthRecord::class,
    ],
    version = 10
)
@TypeConverters(
    AndroidDatabase.DateConverters::class,
    AccountTypeConverters::class,
    SyncIntervalConverters::class,
    SyncOnStartConverters::class,
    SyncOnlyOnWiFiConverters::class,
    SyncOnlyWhenChargingConverters::class,
    KeepArchivedConverters::class,
    SyncBlockListConverters::class,
    FeedErrorTypeConverters::class,
)
abstract class AndroidDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun groupDao(): GroupDao
    abstract fun addedRssSourceDao(): AddedRssSourceDao
    abstract fun articleHighlightDao(): ArticleHighlightDao
    abstract fun feedHealthDao(): FeedHealthDao

    companion object {

        private var instance: AndroidDatabase? = null

        fun getInstance(context: Context): AndroidDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AndroidDatabase::class.java,
                    "Reader"
                ).addMigrations(*allMigrations).build().also {
                    instance = it
                }
            }
        }
    }

    class DateConverters {

        @TypeConverter
        fun toDate(dateLong: Long?): Date? {
            return dateLong?.let { Date(it) }
        }

        @TypeConverter
        fun fromDate(date: Date?): Long? {
            return date?.time
        }
    }
}

val allMigrations = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
)

@Suppress("ClassName")
object MIGRATION_1_2 : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE article ADD COLUMN img TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_2_3 : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE article ADD COLUMN updateAt INTEGER DEFAULT ${System.currentTimeMillis()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncInterval INTEGER NOT NULL DEFAULT ${SyncIntervalPref.default.value}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnStart INTEGER NOT NULL DEFAULT ${SyncOnStartPref.default.value.toInt()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyOnWiFi INTEGER NOT NULL DEFAULT ${SyncOnlyOnWiFiPref.default.value.toInt()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyWhenCharging INTEGER NOT NULL DEFAULT ${SyncOnlyWhenChargingPref.default.value.toInt()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN keepArchived INTEGER NOT NULL DEFAULT ${KeepArchivedPreference.default.value}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncBlockList TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_3_4 : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN securityKey TEXT DEFAULT '${DESUtils.empty}'
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_4_5 : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN lastArticleId TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_5_6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS added_rss_source (
                url TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                addedAt INTEGER NOT NULL,
                accountId INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_added_rss_source_accountId ON added_rss_source(accountId)
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_6_7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE article ADD COLUMN aiSummary TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_article_accountId_isUnread_date ON article(accountId, isUnread, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_article_accountId_feedId_isUnread_date ON article(accountId, feedId, isUnread, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_article_accountId_isStarred_date ON article(accountId, isStarred, date)")
    }
}

@Suppress("ClassName")
object MIGRATION_7_8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS article_highlight_note (
                id TEXT NOT NULL PRIMARY KEY,
                articleId TEXT NOT NULL,
                articleTitle TEXT NOT NULL,
                feedName TEXT NOT NULL,
                articleLink TEXT NOT NULL,
                selectedText TEXT NOT NULL,
                noteComment TEXT NOT NULL,
                colorHex TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_article_highlight_note_articleId ON article_highlight_note(articleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_article_highlight_note_createdAt ON article_highlight_note(createdAt)")
    }
}

@Suppress("ClassName")
object MIGRATION_8_9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `article_fts` USING FTS4(
                `title` TEXT NOT NULL,
                `shortDescription` TEXT NOT NULL,
                `fullContent` TEXT,
                tokenize=unicode61,
                content=`article`
            )
            """.trimIndent()
        )
        // Populate existing articles into FTS table
        db.execSQL("INSERT INTO `article_fts`(`article_fts`) VALUES('rebuild')")
        // Triggers generated by Room to keep FTS table in sync with content table
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_article_fts_BEFORE_UPDATE BEFORE UPDATE ON `article` BEGIN
                DELETE FROM `article_fts` WHERE `docid`=OLD.`rowid`;
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_article_fts_BEFORE_DELETE BEFORE DELETE ON `article` BEGIN
                DELETE FROM `article_fts` WHERE `docid`=OLD.`rowid`;
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_article_fts_AFTER_UPDATE AFTER UPDATE ON `article` BEGIN
                INSERT INTO `article_fts`(`docid`, `title`, `shortDescription`, `fullContent`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`shortDescription`, NEW.`fullContent`);
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_article_fts_AFTER_INSERT AFTER INSERT ON `article` BEGIN
                INSERT INTO `article_fts`(`docid`, `title`, `shortDescription`, `fullContent`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`shortDescription`, NEW.`fullContent`);
            END
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_9_10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `feed_health_record` (
                `feedId` TEXT NOT NULL PRIMARY KEY,
                `lastSuccessTime` INTEGER,
                `lastLatencyMs` INTEGER,
                `lastErrorType` TEXT NOT NULL,
                `lastErrorMessage` TEXT,
                `lastErrorTime` INTEGER
            )
            """.trimIndent()
        )
    }
}



