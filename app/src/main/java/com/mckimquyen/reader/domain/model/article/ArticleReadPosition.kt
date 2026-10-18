package com.mckimquyen.reader.domain.model.article

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_read_position",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["articleId"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["isSynced"])
    ]
)
@Keep
data class ArticleReadPosition(
    @PrimaryKey
    @ColumnInfo(name = "articleId")
    val articleId: String,
    @ColumnInfo(name = "accountId")
    val accountId: Int,
    @ColumnInfo(name = "feedUrl")
    val feedUrl: String,
    @ColumnInfo(name = "articleLink")
    val articleLink: String,
    @ColumnInfo(name = "progress")
    val progress: Float,
    @ColumnInfo(name = "lastReadTime")
    val lastReadTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false
)
