package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition

@Dao
interface ArticleReadPositionDao {

    @Query("SELECT * FROM article_read_position WHERE articleId = :articleId LIMIT 1")
    suspend fun getPosition(articleId: String): ArticleReadPosition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(position: ArticleReadPosition)

    @Query("DELETE FROM article_read_position WHERE articleId = :articleId")
    suspend fun deletePosition(articleId: String)

    @Query("SELECT * FROM article_read_position WHERE isSynced = 0")
    suspend fun getUnsyncedPositions(): List<ArticleReadPosition>

    @Query("UPDATE article_read_position SET isSynced = 1 WHERE articleId = :articleId")
    suspend fun markSynced(articleId: String)
}
