package com.mckimquyen.reader.domain.model.rpg

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "brain_rpg_reward",
    indices = [androidx.room.Index(value = ["articleId", "rewardType"], unique = true)],
)
data class BrainRpgRewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val articleId: String,
    val rewardType: BrainRpgRewardType,
    val createdAtMillis: Long,
)
