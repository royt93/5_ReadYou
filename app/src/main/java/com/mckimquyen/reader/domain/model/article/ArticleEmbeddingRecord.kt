package com.mckimquyen.reader.domain.model.article

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.security.MessageDigest

/** Persistent 64-dimensional semantic-search embedding for one article. */
@Entity(
    tableName = "article_embedding",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
)
@Keep
data class ArticleEmbeddingRecord(
    @PrimaryKey
    @ColumnInfo(name = "articleId")
    val articleId: String,
    @ColumnInfo(name = "contentHash")
    val contentHash: String,
    @ColumnInfo(name = "embedding")
    val embedding: String,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Returns null for corrupt data or a vector created with an incompatible dimension. */
    fun toFloatArray(expectedDimensions: Int): FloatArray? {
        val parts = embedding.split(VECTOR_SEPARATOR)
        if (parts.size != expectedDimensions) return null
        val result = FloatArray(expectedDimensions)
        for (index in parts.indices) {
            result[index] = parts[index].toFloatOrNull() ?: return null
        }
        return result
    }

    companion object {
        const val MAX_DESC_CHARS = 300
        const val EMBEDDING_VERSION = "semantic-v1"
        private const val VECTOR_SEPARATOR = ","
        private const val HEX_RADIX = 16
        private const val HEX_BYTE_MASK = 0xff
        private const val HEX_BYTE_WIDTH = 2

        fun documentText(title: String, shortDescription: String): String =
            "$title ${shortDescription.take(MAX_DESC_CHARS)}"

        /** SHA-256 also includes algorithm version, invalidating cache after embedding changes. */
        fun computeContentHash(title: String, shortDescription: String): String {
            val input = "$EMBEDDING_VERSION\u0000${documentText(title, shortDescription)}"
            return MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte ->
                    (byte.toInt() and HEX_BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
                }
        }

        fun fromFloatArray(
            articleId: String,
            contentHash: String,
            array: FloatArray,
            updatedAt: Long = System.currentTimeMillis(),
        ): ArticleEmbeddingRecord = ArticleEmbeddingRecord(
            articleId = articleId,
            contentHash = contentHash,
            embedding = array.joinToString(VECTOR_SEPARATOR),
            updatedAt = updatedAt,
        )
    }
}
