package com.mckimquyen.reader.domain.model.rpg

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brain_rpg_profile")
data class BrainRpgProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val baseTotalXp: Long = 0L,
    val streakDays: Int = DEFAULT_STREAK_DAYS,
    val lastReadEpochDay: Long = 0L,
    val streakShieldActive: Boolean = false,
    val quizzesAttempted: Int = 0,
    val quizzesPassed: Int = 0,
    val legacyImportComplete: Boolean = false,
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_STREAK_DAYS = 1
    }
}
