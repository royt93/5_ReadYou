package com.mckimquyen.reader.domain.model.radar

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mckimquyen.reader.domain.model.article.Article

/** One captured version of an article's title and capped plain-text body (EXC-10). */
@Entity(
    tableName = "article_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["articleId", "capturedAt"])],
)
@Keep
data class ArticleSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val articleId: String,
    val capturedAt: Long,
    val titleHash: String,
    val contentHash: String,
    val title: String,
    val contentText: String,
)
