package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuizGeneratorServiceTest {

    private lateinit var service: QuizGeneratorService

    @Before
    fun setUp() {
        service = QuizGeneratorService()
    }

    @Test
    fun detectCategory_techKeywords_returnsTech() {
        val cat = service.detectCategory("Google announces new AI chip", "The chip outperforms previous neural models.")
        assertEquals(QuizGeneratorService.CATEGORY_TECH, cat)
    }

    @Test
    fun detectCategory_financeKeywords_returnsBusiness() {
        val cat = service.detectCategory("Fed raises interest rates as inflation cools", "Wall Street stocks jumped on the news.")
        assertEquals(QuizGeneratorService.CATEGORY_BUSINESS, cat)
    }

    @Test
    fun detectCategory_scienceKeywords_returnsScience() {
        val cat = service.detectCategory("NASA discovers water molecules on Europa", "Space exploration probe sends data back to Earth.")
        assertEquals(QuizGeneratorService.CATEGORY_SCIENCE, cat)
    }

    @Test
    fun detectCategory_healthKeywords_returnsHealth() {
        val cat = service.detectCategory("5 Habits to improve sleep and circadian rhythm", "Proper fitness and diet enhance cognitive health.")
        assertEquals(QuizGeneratorService.CATEGORY_HEALTH, cat)
    }

    @Test
    fun detectCategory_philosophyKeywords_returnsPhilosophy() {
        val cat = service.detectCategory("Marcus Aurelius and the Stoic art of emotional resilience", "A study on ancient philosophy and mind ethics.")
        assertEquals(QuizGeneratorService.CATEGORY_PHILOSOPHY, cat)
    }

    @Test
    fun categoryConstantsKeepTheirRoomKeys() {
        assertEquals("Tech & AI", QuizGeneratorService.CATEGORY_TECH)
        assertEquals("Business & Finance", QuizGeneratorService.CATEGORY_BUSINESS)
        assertEquals("Science & Nature", QuizGeneratorService.CATEGORY_SCIENCE)
        assertEquals("Health & Lifestyle", QuizGeneratorService.CATEGORY_HEALTH)
        assertEquals("Philosophy & Ideas", QuizGeneratorService.CATEGORY_PHILOSOPHY)
        assertEquals("General Knowledge", QuizGeneratorService.CATEGORY_GENERAL)
    }

    @Test
    fun theHeadlineQuizGeneratorIsGone() {
        val methods = QuizGeneratorService::class.java.declaredMethods.map { it.name }

        assertTrue("generateQuiz must be removed", methods.none { it == "generateQuiz" })
        assertTrue("detectCategory must stay", methods.contains("detectCategory"))
    }
}
