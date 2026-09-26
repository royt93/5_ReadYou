package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.article.ArticleEmbeddingRecord

/** Persistent semantic-search vectors, keyed by article id (KNOW-05). */
@Dao
interface ArticleEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(records: List<ArticleEmbeddingRecord>)

    @Query("SELECT * FROM article_embedding WHERE articleId IN (:articleIds)")
    suspend fun getByArticleIds(articleIds: List<String>): List<ArticleEmbeddingRecord>

    @Query("SELECT * FROM article_embedding WHERE articleId = :articleId LIMIT 1")
    suspend fun getByArticleId(articleId: String): ArticleEmbeddingRecord?

    @Query("SELECT COUNT(*) FROM article_embedding")
    suspend fun count(): Int
}
