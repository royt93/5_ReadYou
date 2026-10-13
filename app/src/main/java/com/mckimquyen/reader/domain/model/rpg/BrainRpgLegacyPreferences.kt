package com.mckimquyen.reader.domain.model.rpg

import android.content.SharedPreferences

/** Read-only adapter for the pre-Room device-wide Brain RPG profile. */
object BrainRpgLegacyPreferences {
    const val PREFS_NAME = "brain_rpg_prefs"
    const val KEY_TOTAL_XP = "brain_rpg_total_xp"
    const val KEY_STREAK_DAYS = "brain_rpg_streak_days"
    const val KEY_LAST_READ_EPOCH_DAY = "brain_rpg_last_read_epoch_day"
    const val KEY_STREAK_SHIELD_ACTIVE = "brain_rpg_streak_shield_active"
    const val KEY_QUIZZES_ATTEMPTED = "brain_rpg_quizzes_attempted"
    const val KEY_QUIZZES_PASSED = "brain_rpg_quizzes_passed"
    const val KEY_CAT_PREFIX = "brain_rpg_cat"
    const val KEY_READ_REWARDED = "brain_rpg_read_rewarded_ids"
    const val KEY_QUIZ_REWARDED = "brain_rpg_quiz_rewarded_ids"
    const val KEY_DOUBLE_REWARDED = "brain_rpg_double_rewarded_ids"
    const val DEFAULT_STREAK_DAYS = 1

    val knownCategories = listOf("tech", "business", "science", "health", "philosophy", "general")

    fun categoryKey(categoryId: String): String = "${KEY_CAT_PREFIX}_$categoryId"

    fun read(preferences: SharedPreferences): BrainRpgLegacySnapshot {
        val categoryBaseXp = preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith("${KEY_CAT_PREFIX}_") || value !is Long) return@mapNotNull null
            key.removePrefix("${KEY_CAT_PREFIX}_") to value
        }.toMap()
        val rewards = buildSet {
            preferences.getStringSet(KEY_READ_REWARDED, emptySet()).orEmpty().forEach { add(BrainRpgLegacyReward(it, BrainRpgRewardType.READ)) }
            preferences.getStringSet(KEY_QUIZ_REWARDED, emptySet()).orEmpty().forEach { add(BrainRpgLegacyReward(it, BrainRpgRewardType.QUIZ)) }
            preferences.getStringSet(KEY_DOUBLE_REWARDED, emptySet()).orEmpty().forEach { add(BrainRpgLegacyReward(it, BrainRpgRewardType.DOUBLE)) }
        }
        return BrainRpgLegacySnapshot(
            profile = BrainRpgLegacyProfile(
                baseTotalXp = preferences.getLong(KEY_TOTAL_XP, 0L),
                streakDays = preferences.getInt(KEY_STREAK_DAYS, DEFAULT_STREAK_DAYS),
                lastReadEpochDay = preferences.getLong(KEY_LAST_READ_EPOCH_DAY, 0L),
                streakShieldActive = preferences.getBoolean(KEY_STREAK_SHIELD_ACTIVE, false),
                quizzesAttempted = preferences.getInt(KEY_QUIZZES_ATTEMPTED, 0),
                quizzesPassed = preferences.getInt(KEY_QUIZZES_PASSED, 0),
            ),
            categoryBaseXp = categoryBaseXp,
            rewardLedger = rewards,
        )
    }
}

data class BrainRpgLegacySnapshot(
    val profile: BrainRpgLegacyProfile,
    val categoryBaseXp: Map<String, Long>,
    val rewardLedger: Set<BrainRpgLegacyReward>,
)

data class BrainRpgLegacyProfile(
    val baseTotalXp: Long,
    val streakDays: Int,
    val lastReadEpochDay: Long,
    val streakShieldActive: Boolean,
    val quizzesAttempted: Int,
    val quizzesPassed: Int,
)

data class BrainRpgLegacyReward(val articleId: String, val rewardType: BrainRpgRewardType)
