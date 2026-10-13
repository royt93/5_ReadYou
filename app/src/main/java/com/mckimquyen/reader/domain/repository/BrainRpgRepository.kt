package com.mckimquyen.reader.domain.repository

import android.content.Context
import android.content.SharedPreferences
import com.mckimquyen.reader.domain.model.rpg.LevelCalculator
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrainRpgRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    @IODispatcher ioDispatcher: CoroutineDispatcher,
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

    // Starts at the in-memory default; the persisted progress is loaded off the constructing
    // thread (usually Main, when Hilt builds the graph) so SharedPreferences I/O never blocks startup.
    private val _userProgress = MutableStateFlow(UserProgress())
    val userProgress: StateFlow<UserProgress> = _userProgress.asStateFlow()

    @Volatile
    private var isLoaded = false

    init {
        applicationScope.launch(ioDispatcher) { ensureLoaded() }
    }

    fun getProgress(): UserProgress = _userProgress.value

    /**
     * Loads persisted progress exactly once. Every mutation calls this first: if XP is awarded
     * before the async load finished, the stored totals are loaded (synchronously, rare path)
     * before being incremented, so the default in-memory state never overwrites saved progress.
     */
    @Synchronized
    private fun ensureLoaded() {
        if (isLoaded) return
        _userProgress.value = loadProgress()
        isLoaded = true
    }

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
        ensureLoaded()
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

    /**
     * Awards reading XP at most once per article, forever: the set of rewarded article ids lives in storage, so a
     * killed process or a re-created ViewModel cannot farm the same article again. Returns the XP actually awarded
     * (0 when the article was already rewarded or the id is blank).
     */
    @Synchronized
    fun awardReadingXpOnce(articleId: String, category: String, amount: Long = READING_XP): Long {
        if (articleId.isBlank()) return 0L
        ensureLoaded()
        if (!markRewarded(KEY_READ_REWARDED, articleId)) return 0L
        addReadingXp(category, amount)
        return amount
    }

    /**
     * Quiz reward at most once per article. Only a CORRECT answer uses the reward up: a wrong answer is still
     * counted as an attempt but leaves the reward available for a retry. A repeat of a rewarded quiz changes nothing
     * (no XP, no extra attempt/pass count). Returns the updated progress and the XP awarded (0 when blocked).
     */
    @Synchronized
    fun submitQuizResultOnce(articleId: String, category: String, isCorrect: Boolean): Pair<UserProgress, Long> {
        ensureLoaded()
        if (articleId.isBlank()) return submitQuizResult(category, isCorrect)
        if (articleId in rewardedIds(KEY_QUIZ_REWARDED)) return _userProgress.value to 0L
        if (isCorrect) markRewarded(KEY_QUIZ_REWARDED, articleId)
        return submitQuizResult(category, isCorrect)
    }

    private fun rewardedIds(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()

    /** Persists [articleId] in the set at [key]; false when it was already there. Commits synchronously on purpose. */
    private fun markRewarded(key: String, articleId: String): Boolean {
        val current = rewardedIds(key)
        if (articleId in current) return false
        // getStringSet returns an instance that must not be modified, so write a fresh copy.
        return prefs.edit().putStringSet(key, current + articleId).commit()
    }

    @Synchronized
    fun submitQuizResult(category: String, isCorrect: Boolean, xpMultiplier: Int = 1): Pair<UserProgress, Long> {
        ensureLoaded()
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
        ensureLoaded()
        prefs.edit().putBoolean(KEY_STREAK_SHIELD_ACTIVE, true).apply()
        val updated = loadProgress()
        _userProgress.value = updated
        return true
    }

    @Synchronized
    fun awardDoubleXpForQuiz(category: String, bonusXp: Long): UserProgress {
        ensureLoaded()
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
        private const val KEY_READ_REWARDED = "brain_rpg_read_rewarded_ids"
        private const val KEY_QUIZ_REWARDED = "brain_rpg_quiz_rewarded_ids"
        const val READING_XP = 50L
    }
}
