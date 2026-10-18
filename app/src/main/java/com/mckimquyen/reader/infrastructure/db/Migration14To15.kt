package com.mckimquyen.reader.infrastructure.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object MIGRATION_14_15 : Migration(14, 15) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `article_read_position` (
                `articleId` TEXT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `feedUrl` TEXT NOT NULL,
                `articleLink` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                `lastReadTime` INTEGER NOT NULL,
                `isSynced` INTEGER NOT NULL,
                PRIMARY KEY(`articleId`),
                FOREIGN KEY(`articleId`) REFERENCES `article`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_read_position_articleId` ON `article_read_position` (`articleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_read_position_accountId` ON `article_read_position` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_read_position_isSynced` ON `article_read_position` (`isSynced`)")
    }
}
