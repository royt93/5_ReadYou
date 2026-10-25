package com.mckimquyen.reader.domain.repository

import android.content.Context
import androidx.room.withTransaction
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.CategoryDecay
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.domain.model.rpg.LevelCalculator
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrainRpgRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AndroidDatabase,
    private val dao: BrainRpgDao,
    private val legacyImporter: BrainRpgLegacyImporter,
    @ApplicationScope applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutationMutex = Mutex()
    private val refreshMutex = Mutex()
    private val _userProgress = MutableStateFlow(UserProgress())
    val userProgress: StateFlow<UserProgress> = _userProgress.asStateFlow()
    private val _doubleRewardedArticleIds = MutableStateFlow<Set<String>>(emptySet())
    val doubleRewardedArticleIds: StateFlow<Set<String>> = _doubleRewardedArticleIds.asStateFlow()

    init {
        applicationScope.launch(ioDispatcher) { refresh() }
    }

    /** Room is the source of truth; every read awaits the retry-safe legacy import first. */
    suspend fun refresh(nowMillis: Long = System.currentTimeMillis()): UserProgress = refreshMutex.withLock {
        legacyImporter.ensureImported(System.currentTimeMillis())
        val profile = dao.getProfile() ?: BrainRpgProfileEntity().also { dao.upsertProfile(it) }
        val categories = dao.getCategories()
        val level = LevelCalculator.calculateLevel(profile.baseTotalXp)
        val rewards = dao.allRewards()
        val progress = UserProgress(
            totalXp = profile.baseTotalXp,
            level = level.first,
            levelTitle = level.second,
            currentLevelBaseXp = level.third.first,
            nextLevelTargetXp = level.third.second,
            progressFraction = LevelCalculator.calculateFraction(profile.baseTotalXp, level.third.first, level.third.second),
            streakDays = profile.streakDays,
            lastReadEpochDay = profile.lastReadEpochDay,
            streakShieldActive = profile.streakShieldActive,
            categoryXp = categories.associate { category ->
                category.categoryId to CategoryDecay.effectiveXp(category.baseXp, category.lastPracticedAtMillis, nowMillis)
            },
            categoryBaseXp = categories.associate { it.categoryId to it.baseXp },
            quizzesAttempted = profile.quizzesAttempted,
            quizzesPassed = profile.quizzesPassed,
        )
        _userProgress.value = progress
        _doubleRewardedArticleIds.value = rewards.asSequence()
            .filter { it.rewardType == BrainRpgRewardType.DOUBLE }
            .map { it.articleId }
            .toSet()
        progress
    }

    suspend fun getProgress(): UserProgress = refresh()

    /** Award reading XP once; repeat qualifying reads still refresh practice time, but never award XP twice. */
    suspend fun awardReadingXpOnce(articleId: String, category: String, amount: Long = READING_XP, nowMillis: Long = System.currentTimeMillis()): Long {
        if (articleId.isBlank()) return 0L
        return mutationMutex.withLock {
            legacyImporter.ensureImported(nowMillis)
            val before = requireNotNull(dao.getProfile())
            val (streakDays, shieldActive) = nextStreak(before.streakDays, before.lastReadEpochDay, before.streakShieldActive, epochDay(nowMillis))
            val claimed = database.withTransaction {
                dao.claimAndApplyReward(
                    BrainRpgRewardEntity(articleId = articleId, rewardType = BrainRpgRewardType.READ, createdAtMillis = nowMillis),
                    categoryId = category,
                    practiceAtMillis = nowMillis,
                    baseXpDelta = amount,
                    newStreakDays = streakDays,
                    newLastReadEpochDay = epochDay(nowMillis),
                    newStreakShieldActive = shieldActive,
                )
            }
            refresh()
            if (claimed) amount else 0L
        }
    }

    /** Award triage XP once per article; repeat triage calls on the same article return 0. */
    suspend fun awardTriageXpOnce(
        articleId: String,
        amount: Long = 5L,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (articleId.isBlank()) return 0L
        return mutationMutex.withLock {
            legacyImporter.ensureImported(nowMillis)
            val before = dao.getProfile() ?: BrainRpgProfileEntity().also { dao.upsertProfile(it) }
            val (streakDays, shieldActive) = nextStreak(before.streakDays, before.lastReadEpochDay, before.streakShieldActive, epochDay(nowMillis))
            val claimed = database.withTransaction {
                dao.claimAndApplyReward(
                    BrainRpgRewardEntity(
                        articleId = articleId,
                        rewardType = BrainRpgRewardType.TRIAGE,
                        createdAtMillis = nowMillis,
                    ),
                    categoryId = QuizGeneratorService.CATEGORY_GENERAL,
                    practiceAtMillis = nowMillis,
                    baseXpDelta = amount,
                    newStreakDays = streakDays,
                    newLastReadEpochDay = epochDay(nowMillis),
                    newStreakShieldActive = shieldActive,
                )
            }
            refresh()
            if (claimed) amount else 0L
        }
    }

    /** Wrong answers increment attempts only; correct answers practice the category and award once per article. */
    suspend fun submitQuizResultOnce(
        articleId: String,
        category: String,
        isCorrect: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Pair<UserProgress, Long> = mutationMutex.withLock {
        legacyImporter.ensureImported(nowMillis)
        if (articleId.isBlank() || !isCorrect) {
            val updated = database.withTransaction {
                if (!isCorrect) dao.recordWrongQuizAttempt()
                refreshInsideTransaction(nowMillis)
            }
            refresh()
            return@withLock updated to 0L
        }
        val claimed = database.withTransaction {
            dao.claimAndApplyReward(
                BrainRpgRewardEntity(articleId = articleId, rewardType = BrainRpgRewardType.QUIZ, createdAtMillis = nowMillis),
                categoryId = category,
                practiceAtMillis = nowMillis,
                baseXpDelta = QUIZ_XP,
                quizAttemptDelta = 1,
                quizPassDelta = 1,
            )
        }
        val updated = refresh()
        updated to if (claimed) QUIZ_XP else 0L
    }

    suspend fun awardDoubleXpForQuizOnce(articleId: String, category: String, bonusXp: Long): Long {
        if (articleId.isBlank()) return 0L
        return mutationMutex.withLock {
            val now = System.currentTimeMillis()
            legacyImporter.ensureImported(now)
            val claimed = dao.claimDoubleRewardAndApplyXp(
                BrainRpgRewardEntity(articleId = articleId, rewardType = BrainRpgRewardType.DOUBLE, createdAtMillis = now),
                category,
                bonusXp,
            )
            refresh()
            if (claimed) bonusXp else 0L
        }
    }

    suspend fun activateStreakShield(): Boolean = mutationMutex.withLock {
        val now = System.currentTimeMillis()
        legacyImporter.ensureImported(now)
        database.withTransaction {
            val profile = requireNotNull(dao.getProfile())
            dao.updateProfile(profile.copy(streakShieldActive = true))
        }
        refresh()
        true
    }

    /**
     * Called after a rewarded ad was earned. Eligibility is re-validated here (not just before the ad was shown),
     * because the callback is asynchronous and may arrive late or twice. Returns true only when state changed.
     */
    suspend fun recoverCategory(categoryId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (categoryId.isBlank()) return false
        return mutationMutex.withLock {
            legacyImporter.ensureImported(nowMillis)
            val recovered = dao.recoverDecayedCategory(categoryId, nowMillis, CategoryDecay.WEEK_MILLIS)
            if (recovered) refresh(nowMillis)
            recovered
        }
    }

    suspend fun recordWrongQuizAttempt(): UserProgress = mutationMutex.withLock {
        legacyImporter.ensureImported(System.currentTimeMillis())
        dao.recordWrongQuizAttempt()
        refresh()
    }

    private suspend fun refreshInsideTransaction(nowMillis: Long): UserProgress {
        val profile = requireNotNull(dao.getProfile())
        val categories = dao.getCategories()
        val level = LevelCalculator.calculateLevel(profile.baseTotalXp)
        return UserProgress(
            totalXp = profile.baseTotalXp,
            level = level.first,
            levelTitle = level.second,
            currentLevelBaseXp = level.third.first,
            nextLevelTargetXp = level.third.second,
            progressFraction = LevelCalculator.calculateFraction(profile.baseTotalXp, level.third.first, level.third.second),
            streakDays = profile.streakDays,
            lastReadEpochDay = profile.lastReadEpochDay,
            streakShieldActive = profile.streakShieldActive,
            categoryXp = categories.associate { category ->
                category.categoryId to CategoryDecay.effectiveXp(category.baseXp, category.lastPracticedAtMillis, nowMillis)
            },
            categoryBaseXp = categories.associate { it.categoryId to it.baseXp },
            quizzesAttempted = profile.quizzesAttempted,
            quizzesPassed = profile.quizzesPassed,
        )
    }

    private fun nextStreak(current: Int, lastRead: Long, shieldActive: Boolean, today: Long): Pair<Int, Boolean> = when {
        lastRead == 0L -> 1 to shieldActive
        lastRead == today -> current to shieldActive
        lastRead == today - 1 -> (current + 1) to shieldActive
        shieldActive -> (current + 1) to false
        else -> 1 to false
    }

    private fun epochDay(nowMillis: Long): Long = nowMillis / MILLIS_PER_DAY

    private companion object {
        const val READING_XP = 50L
        const val QUIZ_XP = 150L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
