package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import kotlinx.coroutines.flow.Flow

/** Title and short description only: no body column is ever read for the prediction finder (FIX-17). */
data class OracleCandidateRow(val id: String, val title: String, val shortDescription: String, val attempts: Int)

@Dao
interface OracleMarketDao {

    /** Newest articles since [sinceMillis] that are undecided: no row yet, or a PENDING row with attempts left. */
    @Query(
        """
        SELECT a.id AS id, a.title AS title, a.shortDescription AS shortDescription, COALESCE(m.attempts, 0) AS attempts
        FROM article a LEFT JOIN oracle_market m ON m.articleId = a.id
        WHERE a.accountId = :accountId AND a.date >= :sinceMillis AND (m.id IS NULL OR m.status = 'PENDING')
        ORDER BY a.date DESC, a.id DESC LIMIT :limit
        """
    )
    suspend fun candidates(accountId: Int, sinceMillis: Long, limit: Int): List<OracleCandidateRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(market: OracleMarket)

    @Query("SELECT * FROM oracle_market WHERE articleId = :articleId")
    suspend fun byArticle(articleId: String): OracleMarket?

    @Query("SELECT * FROM oracle_market WHERE status = 'HAS_CLAIM'")
    fun observeClaims(): Flow<List<OracleMarket>>

    /** Drops claims that expired before [cutoffMillis] and undecided rows created before it; returns the number removed. */
    @Query(
        """
        DELETE FROM oracle_market
        WHERE (status = 'HAS_CLAIM' AND expiresAt < :cutoffMillis)
           OR (status != 'HAS_CLAIM' AND createdAt < :cutoffMillis)
        """
    )
    suspend fun deleteStale(cutoffMillis: Long): Int
}
