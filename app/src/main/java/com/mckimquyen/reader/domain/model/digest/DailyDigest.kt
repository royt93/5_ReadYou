package com.mckimquyen.reader.domain.model.digest

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "daily_digest", indices = [Index(value = ["accountId", "createdAt"])])
data class DailyDigest(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val headline: String,
    val contentJson: String,
    val sourceArticleIdsJson: String,
    val accountId: Int,
)
