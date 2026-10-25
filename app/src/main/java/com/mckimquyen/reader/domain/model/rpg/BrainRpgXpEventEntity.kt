package com.mckimquyen.reader.domain.model.rpg

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "brain_rpg_xp_event",
    indices = [
        Index(value = ["createdAtMillis"]),
        Index(value = ["categoryId", "createdAtMillis"]),
    ],
)
data class BrainRpgXpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: String,
    val xpDelta: Long,
    val rewardType: BrainRpgRewardType,
    val createdAtMillis: Long,
)
