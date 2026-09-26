package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.feed.FeedHealthRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedHealthDao {
    @Query("SELECT * FROM feed_health_record WHERE feedId = :feedId LIMIT 1")
    suspend fun queryByFeedId(feedId: String): FeedHealthRecord?

    @Query("SELECT * FROM feed_health_record")
    fun observeAll(): Flow<List<FeedHealthRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: FeedHealthRecord)

    @Query("DELETE FROM feed_health_record WHERE feedId = :feedId")
    suspend fun deleteByFeedId(feedId: String)
}
