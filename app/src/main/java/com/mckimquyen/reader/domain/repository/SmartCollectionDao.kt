package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionArticle
import com.mckimquyen.reader.domain.model.collection.SmartCollectionWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartCollectionDao {

    @Transaction
    @Query(
        """
        SELECT c.*, COUNT(ca.articleId) AS articleCount
        FROM smart_collection c
        LEFT JOIN smart_collection_article ca ON c.id = ca.collectionId
        WHERE c.accountId = :accountId
        GROUP BY c.id
        ORDER BY c.createdAt DESC
        """
    )
    fun observeCollectionsWithCount(accountId: Int): Flow<List<SmartCollectionWithCount>>

    @Query("SELECT * FROM smart_collection WHERE accountId = :accountId ORDER BY createdAt DESC")
    suspend fun getCollectionsByAccount(accountId: Int): List<SmartCollection>

    @Query("SELECT * FROM smart_collection WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: String): SmartCollection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: SmartCollection)

    @Query("UPDATE smart_collection SET name = :name, query = :query WHERE id = :id")
    suspend fun updateCollection(id: String, name: String, query: String)

    @Query("DELETE FROM smart_collection_article WHERE collectionId = :collectionId")
    suspend fun deleteMatches(collectionId: String)

    @Query("DELETE FROM smart_collection WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMatches(matches: List<SmartCollectionArticle>): List<Long>

    @Transaction
    suspend fun replaceMatches(collectionId: String, matches: List<SmartCollectionArticle>) {
        deleteMatches(collectionId)
        if (matches.isNotEmpty()) insertMatches(matches)
    }

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE id IN (
            SELECT articleId FROM smart_collection_article
            WHERE collectionId = :collectionId
        )
        ORDER BY date DESC
        """
    )
    fun observeArticlesInCollection(collectionId: String): Flow<List<ArticleWithFeed>>

    @Query("DELETE FROM smart_collection_article WHERE collectionId = :collectionId AND articleId = :articleId")
    suspend fun removeArticleFromCollection(collectionId: String, articleId: String)

    @Query("SELECT COUNT(*) FROM smart_collection_article WHERE collectionId = :collectionId")
    suspend fun countArticles(collectionId: String): Int
}
