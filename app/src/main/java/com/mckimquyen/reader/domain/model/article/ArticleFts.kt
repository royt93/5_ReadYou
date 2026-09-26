package com.mckimquyen.reader.domain.model.article

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * FTS4 virtual table backed by the [Article] table (external content).
 * Enables fast full-text search across article title, description, and content with diacritic-insensitive matching.
 */
@Entity(tableName = "article_fts")
@Fts4(contentEntity = Article::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class ArticleFts(
    val title: String,
    val shortDescription: String,
    val fullContent: String?,
)
