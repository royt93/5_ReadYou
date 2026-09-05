package com.mckimquyen.reader.domain.repository

import android.content.Context
import android.content.SharedPreferences
import com.mckimquyen.reader.domain.model.rpg.LevelCalculator
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrainRpgRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("brain_rpg_prefs", Context.MODE_PRIVATE)
    }

    private val categories = listOf(
        QuizGeneratorService.CATEGORY_TECH,
        QuizGeneratorService.CATEGORY_BUSINESS,
        QuizGeneratorService.CATEGORY_SCIENCE,
        QuizGeneratorService.CATEGORY_HEALTH,
        QuizGeneratorService.CATEGORY_PHILOSOPHY,
        QuizGeneratorService.CATEGORY_GENERAL
    )

    private val _userProgress = MutableStateFlow(loadProgress())
    val userProgress: StateFlow<UserProgress> = _userProgress.asStateFlow()

    fun getProgress(): UserProgress = _userProgress.value

    @Synchronized
    private fun loadProgress(): UserProgress {
        val totalXp = prefs.getLong(KEY_TOTAL_XP, 0L)
        val streakDays = prefs.getInt(KEY_STREAK_DAYS, 1)
        val lastReadEpochDay = prefs.getLong(KEY_LAST_READ_EPOCH_DAY, 0L)
        val streakShieldActive = prefs.getBoolean(KEY_STREAK_SHIELD_ACTIVE, false)
        val quizzesAttempted = prefs.getInt(KEY_QUIZZES_ATTEMPTED, 0)
        val quizzesPassed = prefs.getInt(KEY_QUIZZES_PASSED, 0)

        val catMap = mutableMapOf<String, Long>()
        for (cat in categories) {
            catMap[cat] = prefs.getLong("${KEY_CAT_PREFIX}_$cat", 0L)
        }

        val (level, title, bounds) = LevelCalculator.calculateLevel(totalXp)
        val (base, target) = bounds
        val fraction = LevelCalculator.calculateFraction(totalXp, base, target)

        return UserProgress(
            totalXp = totalXp,
            level = level,
            levelTitle = title,
            currentLevelBaseXp = base,
            nextLevelTargetXp = target,
            progressFraction = fraction,
            streakDays = streakDays,
            lastReadEpochDay = lastReadEpochDay,
            streakShieldActive = streakShieldActive,
            categoryXp = catMap,
            quizzesAttempted = quizzesAttempted,
            quizzesPassed = quizzesPassed
        )
    }

    @Synchronized
    fun addReadingXp(category: String, amount: Long = 50L): UserProgress {
        val current = _userProgress.value
        val todayEpochDay = currentEpochDay()

        var newStreak = current.streakDays
        var newShield = current.streakShieldActive

        if (current.lastReadEpochDay == 0L) {
            newStreak = 1
        } else if (current.lastReadEpochDay == todayEpochDay) {
            // Already read today, streak stays same
        } else if (current.lastReadEpochDay == todayEpochDay - 1) {
            // Consecutive day!
            newStreak += 1
        } else {
            // Missed at least one day
            if (current.streakShieldActive) {
                // Streak saved by shield!
                newStreak += 1
                newShield = false
            } else {
                newStreak = 1
            }
        }

        val newTotalXp = current.totalXp + amount
        val newCatXp = (current.categoryXp[category] ?: 0L) + amount

        prefs.edit()
            .putLong(KEY_TOTAL_XP, newTotalXp)
            .putInt(KEY_STREAK_DAYS, newStreak)
            .putLong(KEY_LAST_READ_EPOCH_DAY, todayEpochDay)
            .putBoolean(KEY_STREAK_SHIELD_ACTIVE, newShield)
            .putLong("${KEY_CAT_PREFIX}_$category", newCatXp)
            .apply()

        val updated = loadProgress()
        _userProgress.value = updated
        return updated
    }

    @Synchronized
    fun submitQuizResult(category: String, isCorrect: Boolean, xpMultiplier: Int = 1): Pair<UserProgress, Long> {
        val current = _userProgress.value
        val baseAward = if (isCorrect) 150L else 0L
        val totalAward = baseAward * xpMultiplier

        val newTotalXp = current.totalXp + totalAward
        val newAttempted = current.quizzesAttempted + 1
        val newPassed = current.quizzesPassed + (if (isCorrect) 1 else 0)
        val newCatXp = (current.categoryXp[category] ?: 0L) + totalAward

        prefs.edit()
            .putLong(KEY_TOTAL_XP, newTotalXp)
            .putInt(KEY_QUIZZES_ATTEMPTED, newAttempted)
            .putInt(KEY_QUIZZES_PASSED, newPassed)
            .putLong("${KEY_CAT_PREFIX}_$category", newCatXp)
            .apply()

        val updated = loadProgress()
        _userProgress.value = updated
        return updated to totalAward
    }

    @Synchronized
    fun activateStreakShield(): Boolean {
        prefs.edit().putBoolean(KEY_STREAK_SHIELD_ACTIVE, true).apply()
        val updated = loadProgress()
        _userProgress.value = updated
        return true
    }

    @Synchronized
    fun awardDoubleXpForQuiz(category: String, bonusXp: Long): UserProgress {
        val current = _userProgress.value
        val newTotalXp = current.totalXp + bonusXp
        val newCatXp = (current.categoryXp[category] ?: 0L) + bonusXp

        prefs.edit()
            .putLong(KEY_TOTAL_XP, newTotalXp)
            .putLong("${KEY_CAT_PREFIX}_$category", newCatXp)
            .apply()

        val updated = loadProgress()
        _userProgress.value = updated
        return updated
    }

    private fun currentEpochDay(): Long {
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
    }

    companion object {
        private const val KEY_TOTAL_XP = "brain_rpg_total_xp"
        private const val KEY_STREAK_DAYS = "brain_rpg_streak_days"
        private const val KEY_LAST_READ_EPOCH_DAY = "brain_rpg_last_read_epoch_day"
        private const val KEY_STREAK_SHIELD_ACTIVE = "brain_rpg_streak_shield_active"
        private const val KEY_QUIZZES_ATTEMPTED = "brain_rpg_quizzes_attempted"
        private const val KEY_QUIZZES_PASSED = "brain_rpg_quizzes_passed"
        private const val KEY_CAT_PREFIX = "brain_rpg_cat"
    }
}
