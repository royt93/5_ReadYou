package com.mckimquyen.reader.domain.model.rpg

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brain_rpg_category")
data class BrainRpgCategoryEntity(
    @PrimaryKey val categoryId: String,
    val baseXp: Long = 0L,
    val lastPracticedAtMillis: Long = 0L,
    val lastDecayNotificationEpochDay: Long = 0L,
)
