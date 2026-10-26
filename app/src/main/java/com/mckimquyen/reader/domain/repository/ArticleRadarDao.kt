package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.radar.ArticleRadarState
import com.mckimquyen.reader.domain.model.radar.ArticleSnapshot
import kotlinx.coroutines.flow.Flow

/** Hash-only projection: never loads snapshot text just to compare. */
data class SnapshotHashRow(val titleHash: String, val contentHash: String)

/** Id/link/date/miss-count of a stored article; no body columns (FIX-17). */
data class TrackedArticleRow(
    val id: String,
    val link: String,
    val date: Long,
    val missedSyncs: Int,
    val removedAt: Long?,
)

@Dao
interface ArticleRadarDao {

    @Query("SELECT titleHash, contentHash FROM article_snapshot WHERE articleId = :articleId ORDER BY capturedAt DESC, id DESC LIMIT 1")
    suspend fun latestHashes(articleId: String): SnapshotHashRow?

    @Insert
    suspend fun insertSnapshot(snapshot: ArticleSnapshot)

    /** Newest first. [limit] keeps the CursorWindow small (each row holds up to 2x capped text). */
    @Query("SELECT * FROM article_snapshot WHERE articleId = :articleId ORDER BY capturedAt DESC, id DESC LIMIT :limit")
    suspend fun snapshots(articleId: String, limit: Int): List<ArticleSnapshot>

    @Query("SELECT * FROM article_snapshot WHERE articleId = :articleId ORDER BY capturedAt ASC, id ASC LIMIT 1")
    suspend fun firstSnapshot(articleId: String): ArticleSnapshot?

    @Query("SELECT COUNT(*) FROM article_snapshot WHERE articleId = :articleId")
    suspend fun snapshotCount(articleId: String): Int

    @Query("SELECT * FROM article_radar_state WHERE articleId = :articleId")
    suspend fun state(articleId: String): ArticleRadarState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: ArticleRadarState)

    /** Only flagged articles; the table stays tiny, so the list UI can observe it whole. */
    @Query("SELECT * FROM article_radar_state WHERE editCount > 0 OR removedAt IS NOT NULL")
    fun observeFlagged(): Flow<List<ArticleRadarState>>

    @Query("SELECT * FROM article_radar_state WHERE articleId = :articleId")
    fun observeState(articleId: String): Flow<ArticleRadarState?>

    @Query("SELECT id FROM article WHERE link = :link AND feedId = :feedId AND accountId = :accountId LIMIT 1")
    suspend fun articleIdByLink(link: String, feedId: String, accountId: Int): String?

    /** Articles of a feed no older than [minDate], with their miss counters, for removal tracking. */
    @Query(
        """
        SELECT a.id AS id, a.link AS link, a.date AS date,
               COALESCE(s.missedSyncs, 0) AS missedSyncs, s.removedAt AS removedAt
        FROM article a LEFT JOIN article_radar_state s ON s.articleId = a.id
        WHERE a.feedId = :feedId AND a.accountId = :accountId AND a.date >= :minDate
        """
    )
    suspend fun tracked(feedId: String, accountId: Int, minDate: Long): List<TrackedArticleRow>

    /** Keeps each article's original snapshot plus its newest [keepLatest]; drops the rest. */
    @Query(
        """
        DELETE FROM article_snapshot
        WHERE id NOT IN (SELECT MIN(id) FROM article_snapshot GROUP BY articleId)
          AND id NOT IN (
              SELECT s2.id FROM article_snapshot s2
              WHERE s2.articleId = article_snapshot.articleId
              ORDER BY s2.capturedAt DESC, s2.id DESC LIMIT :keepLatest
          )
        """
    )
    suspend fun trimSnapshots(keepLatest: Int)

    @Query("DELETE FROM article_snapshot WHERE articleId IN (SELECT articleId FROM article_radar_state WHERE removedAt IS NOT NULL AND removedAt < :cutoff)")
    suspend fun deleteSnapshotsOfLongRemoved(cutoff: Long)

    @Query("DELETE FROM article_radar_state WHERE removedAt IS NOT NULL AND removedAt < :cutoff")
    suspend fun deleteLongRemovedState(cutoff: Long)
}
