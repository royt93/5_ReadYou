package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType

@Dao
abstract class BrainRpgDao {
    @Query("SELECT * FROM brain_rpg_profile WHERE id = 1")
    abstract suspend fun getProfile(): BrainRpgProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertProfile(profile: BrainRpgProfileEntity)

    @Update
    abstract suspend fun updateProfile(profile: BrainRpgProfileEntity)

    @Query("SELECT * FROM brain_rpg_category ORDER BY categoryId")
    abstract suspend fun getCategories(): List<BrainRpgCategoryEntity>

    @Query("SELECT * FROM brain_rpg_category WHERE categoryId = :categoryId")
    abstract suspend fun getCategory(categoryId: String): BrainRpgCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCategory(category: BrainRpgCategoryEntity)

    @Query("SELECT * FROM brain_rpg_reward ORDER BY articleId, rewardType")
    abstract suspend fun allRewards(): List<BrainRpgRewardEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM brain_rpg_reward WHERE articleId = :articleId AND rewardType = :rewardType)")
    abstract suspend fun hasReward(articleId: String, rewardType: BrainRpgRewardType): Boolean

    /** Returns true only for the transaction that inserted a previously unseen unique reward key. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRewardIfAbsent(reward: BrainRpgRewardEntity): Long

    @Transaction
    open suspend fun claimReward(reward: BrainRpgRewardEntity): Boolean = insertRewardIfAbsent(reward) != -1L
}
