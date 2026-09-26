package com.mckimquyen.reader.domain.model.feed

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class FeedErrorType {
    NONE,
    HTTP,
    PARSER,
    TIMEOUT,
    NETWORK,
}

class FeedErrorTypeConverters {
    @TypeConverter
    fun toErrorType(name: String?): FeedErrorType =
        name?.let { runCatching { FeedErrorType.valueOf(it) }.getOrNull() } ?: FeedErrorType.NONE

    @TypeConverter
    fun fromErrorType(type: FeedErrorType?): String =
        (type ?: FeedErrorType.NONE).name
}

@Keep
@Entity(tableName = "feed_health_record")
data class FeedHealthRecord(
    @PrimaryKey
    val feedId: String,
    @ColumnInfo
    val lastSuccessTime: Long? = null,
    @ColumnInfo
    val lastLatencyMs: Long? = null,
    @ColumnInfo
    val lastErrorType: FeedErrorType = FeedErrorType.NONE,
    @ColumnInfo
    val lastErrorMessage: String? = null,
    @ColumnInfo
    val lastErrorTime: Long? = null,
) {
    val isFailing: Boolean
        get() = lastErrorType != FeedErrorType.NONE
}
