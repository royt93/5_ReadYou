package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.digest.DailyDigest

@Dao
interface DailyDigestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(digest: DailyDigest)

    @Query("SELECT * FROM daily_digest WHERE accountId = :accountId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun queryRecent(accountId: Int, limit: Int = 20): List<DailyDigest>

    @Query("SELECT * FROM daily_digest WHERE id = :id AND accountId = :accountId LIMIT 1")
    suspend fun queryById(id: String, accountId: Int): DailyDigest?
}
