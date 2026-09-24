package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user article highlights and personal notes.
 */
@Dao
interface ArticleHighlightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: ArticleHighlightNote): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(highlights: List<ArticleHighlightNote>)

    @Update
    suspend fun update(highlight: ArticleHighlightNote)

    @Delete
    suspend fun delete(highlight: ArticleHighlightNote)

    @Query("DELETE FROM article_highlight_note WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM article_highlight_note WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: String)

    @Query("SELECT * FROM article_highlight_note WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ArticleHighlightNote?

    @Query("SELECT * FROM article_highlight_note WHERE articleId = :articleId ORDER BY createdAt ASC")
    fun queryByArticleId(articleId: String): Flow<List<ArticleHighlightNote>>

    @Query("SELECT * FROM article_highlight_note WHERE articleId = :articleId ORDER BY createdAt ASC")
    suspend fun getListByArticleId(articleId: String): List<ArticleHighlightNote>

    @Query("SELECT * FROM article_highlight_note ORDER BY createdAt DESC")
    fun queryAll(): Flow<List<ArticleHighlightNote>>

    @Query("SELECT * FROM article_highlight_note ORDER BY createdAt DESC")
    suspend fun getAllList(): List<ArticleHighlightNote>

    @Query("SELECT * FROM article_highlight_note WHERE colorHex = :colorHex ORDER BY createdAt DESC")
    fun queryByColor(colorHex: String): Flow<List<ArticleHighlightNote>>

    @Query("""
        SELECT * FROM article_highlight_note 
        WHERE selectedText LIKE '%' || :query || '%' 
           OR noteComment LIKE '%' || :query || '%' 
           OR articleTitle LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun search(query: String): Flow<List<ArticleHighlightNote>>

    @Query("SELECT COUNT(*) FROM article_highlight_note")
    fun queryCount(): Flow<Int>
}
