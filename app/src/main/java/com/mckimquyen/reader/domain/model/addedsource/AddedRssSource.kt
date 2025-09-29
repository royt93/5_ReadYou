package com.mckimquyen.reader.domain.model.addedsource

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "added_rss_source")
data class AddedRssSource(
    @PrimaryKey
    @ColumnInfo
    val url: String,
    @ColumnInfo
    val name: String,
    @ColumnInfo
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(index = true)
    val accountId: Int
)