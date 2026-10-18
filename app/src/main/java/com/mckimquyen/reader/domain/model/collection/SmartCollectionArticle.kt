package com.mckimquyen.reader.domain.model.collection

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.mckimquyen.reader.domain.model.article.Article

@Keep
@Entity(
    tableName = "smart_collection_article",
    primaryKeys = ["collectionId", "articleId"],
    foreignKeys = [
        ForeignKey(
            entity = SmartCollection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["collectionId"]),
        Index(value = ["articleId"]),
    ],
)
data class SmartCollectionArticle(
    val collectionId: String,
    val articleId: String,
    val score: Float,
    val matchedAt: Long = System.currentTimeMillis(),
)
