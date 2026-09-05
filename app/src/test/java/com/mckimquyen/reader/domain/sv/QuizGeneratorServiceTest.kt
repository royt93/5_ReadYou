package com.mckimquyen.reader.domain.sv

import org.junit.Assert.*
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
    fun generateQuiz_producesValidQuestionAndOptions() {
        val quiz = service.generateQuiz(
            articleId = "art_12345",
            title = "Breakthrough in Quantum Computing Architecture",
            content = "Researchers have successfully stabilized 1,000 qubits at room temperature. This enables faster simulations for pharmaceutical development.",
            author = "Dr. Alice"
        )

        assertNotNull(quiz)
        assertEquals("art_12345", quiz.articleId)
        assertTrue(quiz.question.isNotBlank())
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.correctAnswerIndex in 0..3)
        assertTrue(quiz.explanation.isNotBlank())
        assertTrue(quiz.options[quiz.correctAnswerIndex].isNotBlank())
    }

    @Test
    fun generateQuiz_deterministicForSameArticleId() {
        val quiz1 = service.generateQuiz("article_xyz", "Title A", "Content A", "Author")
        val quiz2 = service.generateQuiz("article_xyz", "Title A", "Content A", "Author")

        assertEquals(quiz1.question, quiz2.question)
        assertEquals(quiz1.options, quiz2.options)
        assertEquals(quiz1.correctAnswerIndex, quiz2.correctAnswerIndex)
    }

    @Test
    fun generateQuiz_vietnameseArticle_producesVietnameseQuiz() {
        val quiz = service.generateQuiz(
            articleId = "art_vi_1",
            title = "Việt Nam phát triển thành công mô hình ngôn ngữ lớn tiếng Việt",
            content = "Các chuyên gia công nghệ đã công bố mô hình AI mã nguồn mở cho người dùng Việt Nam và doanh nghiệp trong nước.",
            author = "Nguyễn Văn A"
        )

        assertTrue(quiz.question.isNotBlank())
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.correctAnswerIndex in 0..3)
    }
}
