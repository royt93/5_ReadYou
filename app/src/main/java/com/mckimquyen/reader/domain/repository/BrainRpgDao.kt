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
import com.mckimquyen.reader.domain.model.rpg.BrainRpgXpEventEntity
import com.mckimquyen.reader.domain.model.rpg.CategoryXpTotal

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

    @Query("UPDATE brain_rpg_category SET lastPracticedAtMillis = :practicedAtMillis WHERE categoryId = :categoryId")
    protected abstract suspend fun updatePracticeTimestamp(categoryId: String, practicedAtMillis: Long)

    @Query("UPDATE brain_rpg_category SET baseXp = :baseXp WHERE categoryId = :categoryId")
    protected abstract suspend fun updateCategoryBaseXp(categoryId: String, baseXp: Long)

    @Query("UPDATE brain_rpg_category SET lastDecayNotificationEpochDay = :epochDay WHERE categoryId = :categoryId")
    abstract suspend fun updateDecayNotificationDay(categoryId: String, epochDay: Long)

    @Query("UPDATE brain_rpg_profile SET baseTotalXp = :baseTotalXp, streakDays = :streakDays, lastReadEpochDay = :lastReadEpochDay, streakShieldActive = :streakShieldActive, quizzesAttempted = :quizzesAttempted, quizzesPassed = :quizzesPassed WHERE id = :profileId")
    protected abstract suspend fun updateProfileAwards(profileId: Int, baseTotalXp: Long, streakDays: Int, lastReadEpochDay: Long, streakShieldActive: Boolean, quizzesAttempted: Int, quizzesPassed: Int)

    @Query("UPDATE brain_rpg_profile SET quizzesAttempted = quizzesAttempted + 1 WHERE id = :profileId")
    protected abstract suspend fun incrementWrongQuizAttempt(profileId: Int)

    @Query("SELECT * FROM brain_rpg_reward ORDER BY articleId, rewardType")
    abstract suspend fun allRewards(): List<BrainRpgRewardEntity>

    @Query("SELECT * FROM brain_rpg_xp_event ORDER BY id")
    abstract suspend fun allXpEvents(): List<BrainRpgXpEventEntity>

    @Query("SELECT COALESCE(SUM(xpDelta), 0) FROM brain_rpg_xp_event WHERE createdAtMillis >= :startMillis AND createdAtMillis < :endMillis")
    abstract suspend fun queryWeeklyXpTotal(startMillis: Long, endMillis: Long): Long

    @Query("SELECT categoryId, SUM(xpDelta) AS xp FROM brain_rpg_xp_event WHERE createdAtMillis >= :startMillis AND createdAtMillis < :endMillis GROUP BY categoryId ORDER BY xp DESC, categoryId ASC")
    abstract suspend fun queryWeeklyCategoryXp(startMillis: Long, endMillis: Long): List<CategoryXpTotal>

    @Insert
    abstract suspend fun insertXpEvent(event: BrainRpgXpEventEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM brain_rpg_reward WHERE articleId = :articleId AND rewardType = :rewardType)")
    abstract suspend fun hasReward(articleId: String, rewardType: BrainRpgRewardType): Boolean

    /** Returns true only for the transaction that inserted a previously unseen unique reward key. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRewardIfAbsent(reward: BrainRpgRewardEntity): Long

    @Transaction
    open suspend fun claimReward(reward: BrainRpgRewardEntity): Boolean = insertRewardIfAbsent(reward) != -1L

    /** A qualifying repeated practice refreshes decay even when its XP ledger claim already exists. */
    @Transaction
    open suspend fun recordPractice(categoryId: String, practicedAtMillis: Long) {
        val current = getCategory(categoryId)
        if (current == null) upsertCategory(BrainRpgCategoryEntity(categoryId, lastPracticedAtMillis = practicedAtMillis))
        else updatePracticeTimestamp(categoryId, practicedAtMillis)
    }

    /**
     * Atomically records a qualifying practice, then awards XP/counters only if the ledger claim is new.
     * Practice is intentionally independent from reward idempotency: a repeat read/correct quiz still refreshes decay.
     */
    @Transaction
    open suspend fun claimAndApplyReward(
        reward: BrainRpgRewardEntity,
        categoryId: String,
        practiceAtMillis: Long,
        baseXpDelta: Long,
        quizAttemptDelta: Int = 0,
        quizPassDelta: Int = 0,
        newStreakDays: Int? = null,
        newLastReadEpochDay: Long? = null,
        newStreakShieldActive: Boolean? = null,
        failBeforeCommit: Boolean = false,
    ): Boolean {
        recordPractice(categoryId, practiceAtMillis)
        val claimed = claimReward(reward)
        if (failBeforeCommit) error("test transaction failure")
        val profile = requireNotNull(getProfile())
        updateProfileAwards(
            BrainRpgProfileEntity.SINGLETON_ID,
            profile.baseTotalXp + if (claimed) baseXpDelta else 0L,
            newStreakDays ?: profile.streakDays,
            newLastReadEpochDay ?: profile.lastReadEpochDay,
            newStreakShieldActive ?: profile.streakShieldActive,
            profile.quizzesAttempted + if (claimed) quizAttemptDelta else 0,
            profile.quizzesPassed + if (claimed) quizPassDelta else 0,
        )
        if (claimed) {
            val category = requireNotNull(getCategory(categoryId))
            updateCategoryBaseXp(categoryId, category.baseXp + baseXpDelta)
            insertXpEvent(
                BrainRpgXpEventEntity(
                    categoryId = categoryId,
                    xpDelta = baseXpDelta,
                    rewardType = reward.rewardType,
                    createdAtMillis = reward.createdAtMillis,
                ),
            )
        }
        return claimed
    }

    @Query("SELECT * FROM brain_rpg_reward WHERE articleId = :articleId AND rewardType = :rewardType LIMIT 1")
    protected abstract suspend fun rewardRow(articleId: String, rewardType: BrainRpgRewardType): BrainRpgRewardEntity?

    @Transaction
    open suspend fun claimDoubleRewardAndApplyXp(reward: BrainRpgRewardEntity, categoryId: String, bonusXp: Long): Boolean {
        if (!hasReward(reward.articleId, BrainRpgRewardType.QUIZ)) return false
        if (insertRewardIfAbsent(reward) == -1L) return false
        val profile = requireNotNull(getProfile())
        val category = getCategory(categoryId) ?: BrainRpgCategoryEntity(categoryId)
        if (getCategory(categoryId) == null) upsertCategory(category)
        updateProfileAwards(BrainRpgProfileEntity.SINGLETON_ID, profile.baseTotalXp + bonusXp, profile.streakDays,
            profile.lastReadEpochDay, profile.streakShieldActive, profile.quizzesAttempted, profile.quizzesPassed)
        updateCategoryBaseXp(categoryId, category.baseXp + bonusXp)
        insertXpEvent(
            BrainRpgXpEventEntity(
                categoryId = categoryId,
                xpDelta = bonusXp,
                rewardType = reward.rewardType,
                createdAtMillis = reward.createdAtMillis,
            ),
        )
        return true
    }

    /**
     * Rewarded recovery: resets only [categoryId]'s practice time, and only while it has actually decayed
     * (>= 1 full week, XP > 0, clock not rolled back). Eligibility and write share one transaction, so a late or
     * duplicate ad callback cannot recover twice. Never touches XP, counters or the reward ledger.
     */
    @Transaction
    open suspend fun recoverDecayedCategory(categoryId: String, nowMillis: Long, minDecayMillis: Long): Boolean {
        val category = getCategory(categoryId) ?: return false
        if (category.baseXp <= 0L || nowMillis - category.lastPracticedAtMillis < minDecayMillis) return false
        updatePracticeTimestamp(categoryId, nowMillis)
        return true
    }

    @Transaction
    open suspend fun recordWrongQuizAttempt() {
        val current = getProfile() ?: BrainRpgProfileEntity()
        upsertProfile(current)
        incrementWrongQuizAttempt(BrainRpgProfileEntity.SINGLETON_ID)
    }
}
