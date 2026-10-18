package com.mckimquyen.reader.domain.model.statistics

object HeatLevel {
    const val LEVEL_NONE = 0
    const val LEVEL_MAX = 4

    private const val LEVEL_1_MAX_COUNT = 1
    private const val LEVEL_2_MAX_COUNT = 3
    private const val LEVEL_3_MAX_COUNT = 6

    fun of(count: Int): Int = when {
        count <= 0 -> LEVEL_NONE
        count <= LEVEL_1_MAX_COUNT -> 1
        count <= LEVEL_2_MAX_COUNT -> 2
        count <= LEVEL_3_MAX_COUNT -> 3
        else -> LEVEL_MAX
    }
}
