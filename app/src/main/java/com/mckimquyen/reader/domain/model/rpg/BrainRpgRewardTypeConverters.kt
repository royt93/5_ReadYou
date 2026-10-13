package com.mckimquyen.reader.domain.model.rpg

import androidx.room.TypeConverter

class BrainRpgRewardTypeConverters {
    @TypeConverter
    fun toStorage(value: BrainRpgRewardType): String = value.name

    @TypeConverter
    fun fromStorage(value: String): BrainRpgRewardType = BrainRpgRewardType.valueOf(value)
}
