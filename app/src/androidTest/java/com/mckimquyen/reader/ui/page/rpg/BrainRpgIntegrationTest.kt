package com.mckimquyen.reader.ui.page.rpg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrainRpgIntegrationTest {

    private lateinit var context: Context
    private lateinit var repository: BrainRpgRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear SharedPreferences before test
        context.getSharedPreferences("brain_rpg_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        repository = BrainRpgRepository(context)
    }

    @Test
    fun fullRpgProgressionFlow_integration() {
        // 1. Initial baseline
        var current = repository.userProgress.value
        assertEquals(0L, current.totalXp)
        assertEquals(1, current.level)
        assertEquals("Novice Scholar", current.levelTitle)
        assertEquals(1, current.streakDays)
        assertFalse(current.streakShieldActive)

        // 2. Reading article: add 50 XP to Tech
        repository.addReadingXp(QuizGeneratorService.CATEGORY_TECH, 50L)
        current = repository.userProgress.value
        assertEquals(50L, current.totalXp)
        assertEquals(1, current.level)
        assertEquals(50L, current.categoryXp[QuizGeneratorService.CATEGORY_TECH])

        // 3. Complete Quiz: +150 XP, level up to Level 2
        repository.submitQuizResult(QuizGeneratorService.CATEGORY_TECH, isCorrect = true)
        current = repository.userProgress.value
        assertEquals(200L, current.totalXp)
        assertEquals(2, current.level)
        assertEquals("Curious Reader", current.levelTitle)
        assertEquals(1, current.quizzesAttempted)
        assertEquals(1, current.quizzesPassed)
        assertEquals(100, current.quizAccuracyPercent)

        // 4. Double Quiz reward with ad: +150 XP -> 350 XP -> Level 3
        repository.awardDoubleXpForQuiz(QuizGeneratorService.CATEGORY_TECH, 150L)
        current = repository.userProgress.value
        assertEquals(350L, current.totalXp)
        assertEquals(3, current.level)
        assertEquals("Knowledge Seeker", current.levelTitle)

        // 5. Activate streak shield
        repository.activateStreakShield()
        current = repository.userProgress.value
        assertTrue(current.streakShieldActive)

        // 6. Persistence check: recreate repository from SharedPreferences
        val reloadedRepository = BrainRpgRepository(context)
        val persisted = reloadedRepository.userProgress.value
        assertEquals(350L, persisted.totalXp)
        assertEquals(3, persisted.level)
        assertEquals("Knowledge Seeker", persisted.levelTitle)
        assertTrue(persisted.streakShieldActive)
        assertEquals(100, persisted.quizAccuracyPercent)
    }
}
