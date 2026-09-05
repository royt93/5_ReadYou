package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.*
import org.junit.Test

class BrainRpgModelTest {

    @Test
    fun calculateLevel_zeroXp_levelOne() {
        val (level, title, bounds) = LevelCalculator.calculateLevel(0L)
        assertEquals(1, level)
        assertEquals("Novice Scholar", title)
        assertEquals(0L, bounds.first)
        assertEquals(100L, bounds.second)
    }

    @Test
    fun calculateLevel_progressionThroughThresholds() {
        val lvl2 = LevelCalculator.calculateLevel(100L)
        assertEquals(2, lvl2.first)
        assertEquals("Curious Reader", lvl2.second)

        val lvl3 = LevelCalculator.calculateLevel(300L)
        assertEquals(3, lvl3.first)
        assertEquals("Knowledge Seeker", lvl3.second)

        val lvl4 = LevelCalculator.calculateLevel(650L)
        assertEquals(4, lvl4.first)
        assertEquals("Insightful Thinker", lvl4.second)

        val lvl5 = LevelCalculator.calculateLevel(1150L)
        assertEquals(5, lvl5.first)
        assertEquals("Master Curator", lvl5.second)

        val lvl6 = LevelCalculator.calculateLevel(1850L)
        assertEquals(6, lvl6.first)
        assertEquals("Grandmaster Savant", lvl6.second)

        val lvl7 = LevelCalculator.calculateLevel(2800L)
        assertEquals(7, lvl7.first)
        assertEquals("Omniscient Sage", lvl7.second)
    }

    @Test
    fun calculateFraction_calculatesCorrectPercentage() {
        val fractionMid = LevelCalculator.calculateFraction(50L, 0L, 100L)
        assertEquals(0.5f, fractionMid, 0.001f)

        val fractionStart = LevelCalculator.calculateFraction(0L, 0L, 100L)
        assertEquals(0f, fractionStart, 0.001f)

        val fractionEnd = LevelCalculator.calculateFraction(100L, 0L, 100L)
        assertEquals(1f, fractionEnd, 0.001f)
    }

    @Test
    fun userProgress_quizAccuracyPercent_calculatesCorrectly() {
        val emptyProgress = UserProgress()
        assertEquals(0, emptyProgress.quizAccuracyPercent)

        val halfwayProgress = UserProgress(quizzesAttempted = 10, quizzesPassed = 8)
        assertEquals(80, halfwayProgress.quizAccuracyPercent)

        val perfectProgress = UserProgress(quizzesAttempted = 5, quizzesPassed = 5)
        assertEquals(100, perfectProgress.quizAccuracyPercent)

        val zeroPassed = UserProgress(quizzesAttempted = 3, quizzesPassed = 0)
        assertEquals(0, zeroPassed.quizAccuracyPercent)
    }

    @Test
    fun calculateLevel_extremeHighXp_staysOmniscientSage() {
        val (level, title, bounds) = LevelCalculator.calculateLevel(50_000L)
        assertEquals(7, level)
        assertEquals("Omniscient Sage", title)
        assertEquals(2800L, bounds.first)
        assertEquals(4300L, bounds.second)
    }

    @Test
    fun calculateFraction_edgeCases_noDivideByZero() {
        // Equal bounds
        val fractionEqual = LevelCalculator.calculateFraction(100L, 100L, 100L)
        assertEquals(0f, fractionEqual, 0.001f)

        // Overflow progress capped at 1f
        val fractionOverflow = LevelCalculator.calculateFraction(500L, 0L, 100L)
        assertEquals(1f, fractionOverflow, 0.001f)

        // Negative xp clamped at 0f
        val fractionNegative = LevelCalculator.calculateFraction(-50L, 0L, 100L)
        assertEquals(0f, fractionNegative, 0.001f)
    }
}
