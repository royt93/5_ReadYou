package com.mckimquyen.reader.domain.repository

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.mckimquyen.reader.domain.model.rpg.BrainRpgCategoryEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One-time, retry-safe import from the legacy device-wide SharedPreferences profile into Room. */
class BrainRpgLegacyImporter(
    private val database: AndroidDatabase,
    private val preferences: SharedPreferences,
    private val dao: BrainRpgDao = database.brainRpgDao(),
) {
    private val mutex = Mutex()

    /** Returns true only for the call that performed the import; subsequent/restarted calls return false. */
    suspend fun ensureImported(nowMillis: Long): Boolean = mutex.withLock {
        if (dao.getProfile()?.legacyImportComplete == true) return@withLock false
        val snapshot = BrainRpgLegacyPreferences.read(preferences)
        val imported = database.withTransaction {
            // Recheck inside the transaction in case another importer instance committed while this one waited.
            if (dao.getProfile()?.legacyImportComplete == true) return@withTransaction false

            dao.upsertProfile(
                BrainRpgProfileEntity(
                    id = BrainRpgProfileEntity.SINGLETON_ID,
                    baseTotalXp = snapshot.profile.baseTotalXp,
                    streakDays = snapshot.profile.streakDays,
                    lastReadEpochDay = snapshot.profile.lastReadEpochDay,
                    streakShieldActive = snapshot.profile.streakShieldActive,
                    quizzesAttempted = snapshot.profile.quizzesAttempted,
                    quizzesPassed = snapshot.profile.quizzesPassed,
                    legacyImportComplete = false,
                )
            )
            snapshot.categoryBaseXp.forEach { (categoryId, baseXp) ->
                dao.upsertCategory(
                    BrainRpgCategoryEntity(
                        categoryId = categoryId,
                        baseXp = baseXp,
                        // Existing XP must not decay immediately upon upgrading.
                        lastPracticedAtMillis = nowMillis,
                    )
                )
            }
            snapshot.rewardLedger.forEach { legacy ->
                dao.claimReward(
                    BrainRpgRewardEntity(
                        articleId = legacy.articleId,
                        rewardType = legacy.rewardType,
                        createdAtMillis = nowMillis,
                    )
                )
            }
            dao.updateProfile(requireNotNull(dao.getProfile()).copy(legacyImportComplete = true))
            true
        }
        imported
    }
}
