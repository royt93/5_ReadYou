package com.mckimquyen.reader.domain.model.notebook

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a user-highlighted quotation from an article along with optional personal notes/reflections.
 *
 * @param id Unique identifier (UUID).
 * @param articleId The ID of the article containing the highlight.
 * @param articleTitle Title of the article for direct display in the notebook.
 * @param feedName Source feed name.
 * @param articleLink URL of the article.
 * @param selectedText The exact excerpt highlighted by the user.
 * @param noteComment Optional personal insight, reflection, or counter-argument.
 * @param colorHex Highlight color in hex format (e.g. #FFF176).
 * @param createdAt Creation timestamp in milliseconds.
 */
@Keep
@Entity(
    tableName = "article_highlight_note",
    indices = [
        Index(value = ["articleId"]),
        Index(value = ["createdAt"]),
    ]
)
data class ArticleHighlightNote(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val articleId: String,
    val articleTitle: String = "",
    val feedName: String = "",
    val articleLink: String = "",
    val selectedText: String,
    val noteComment: String = "",
    val colorHex: String = HighlightColor.YELLOW.hex,
    val createdAt: Long = System.currentTimeMillis(),
)
