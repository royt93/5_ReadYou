package com.mckimquyen.reader.domain.model.rpg

data class UserProgress(
    val totalXp: Long = 0L,
    val level: Int = 1,
    val levelTitle: String = "Novice Scholar",
    val currentLevelBaseXp: Long = 0L,
    val nextLevelTargetXp: Long = 100L,
    val progressFraction: Float = 0f,
    val streakDays: Int = 1,
    val lastReadEpochDay: Long = 0L,
    val streakShieldActive: Boolean = false,
    val categoryXp: Map<String, Long> = emptyMap(),
    val quizzesAttempted: Int = 0,
    val quizzesPassed: Int = 0
) {
    val quizAccuracyPercent: Int
        get() = if (quizzesAttempted > 0) ((quizzesPassed.toFloat() / quizzesAttempted) * 100).toInt() else 0
}

object LevelCalculator {
    private val levelThresholds = listOf(
        0L to "Novice Scholar",          // Lvl 1
        100L to "Curious Reader",        // Lvl 2
        300L to "Knowledge Seeker",      // Lvl 3
        650L to "Insightful Thinker",    // Lvl 4
        1150L to "Master Curator",       // Lvl 5
        1850L to "Grandmaster Savant",   // Lvl 6
        2800L to "Omniscient Sage"       // Lvl 7+
    )

    fun calculateLevel(totalXp: Long): Triple<Int, String, Pair<Long, Long>> {
        var currentLevel = 1
        var title = levelThresholds[0].second
        var base = 0L
        var target = 100L

        for (i in levelThresholds.indices) {
            val (reqXp, lvlTitle) = levelThresholds[i]
            if (totalXp >= reqXp) {
                currentLevel = i + 1
                title = lvlTitle
                base = reqXp
                target = if (i + 1 < levelThresholds.size) levelThresholds[i + 1].first else reqXp + 1500L
            } else {
                break
            }
        }
        return Triple(currentLevel, title, base to target)
    }

    fun calculateFraction(totalXp: Long, base: Long, target: Long): Float {
        val range = (target - base).coerceAtLeast(1L)
        val progress = (totalXp - base).coerceAtLeast(0L)
        return (progress.toFloat() / range).coerceIn(0f, 1f)
    }
}
