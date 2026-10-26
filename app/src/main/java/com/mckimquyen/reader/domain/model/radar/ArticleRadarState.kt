package com.mckimquyen.reader.domain.model.radar

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.mckimquyen.reader.domain.model.article.Article

/** Edit/removal flags for an article, kept outside `article` so lists and FTS are untouched (EXC-10). */
@Entity(
    tableName = "article_radar_state",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@Keep
data class ArticleRadarState(
    @PrimaryKey val articleId: String,
    val editCount: Int = 0,
    val lastEditedAt: Long? = null,
    val missedSyncs: Int = 0,
    val removedAt: Long? = null,
)
