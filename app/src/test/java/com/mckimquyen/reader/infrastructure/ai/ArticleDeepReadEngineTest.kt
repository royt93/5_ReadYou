package com.mckimquyen.reader.infrastructure.ai

import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import com.mckimquyen.reader.domain.model.article.DeepReadSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleDeepReadEngineTest {

    private val techArticleTitle = "Next-Generation Agentic AI Architectures in 2026"
    private val techArticleBody = """
        Autonomous agentic systems are reshaping the software engineering landscape in 2026.
        Instead of traditional single-prompt responses, autonomous agents leverage tool calling,
        hierarchical planning, and persistent memory to solve multi-stage engineering challenges.
        
        However, deploying autonomous agents introduces critical security risks including prompt injection,
        unintended tool invocation, and resource exhaustion. Guardrails must be enforced.
        
        According to recent benchmarks, agentic workflows delivered an 85% increase in task completion
        and generated $12 million in operational cost savings for early enterprise adopters.
        
        In conclusion, engineering organizations must embrace automated agent evaluation frameworks
        while maintaining strict human-in-the-loop oversight for high-stakes actions.
    """.trimIndent()

    private val viArticleTitle = "Đột phá Trí tuệ Nhân tạo và Thị trường Bán dẫn 2026"
    private val viArticleBody = """
        Các tập đoàn công nghệ lớn đang đẩy mạnh phát triển chip AI chuyên dụng để đáp ứng nhu cầu tính toán.
        Thị trường phần cứng bán dẫn ghi nhận mức tăng trưởng 35% với doanh thu ước tính vượt 150 tỷ USD trong năm nay.
        
        Tuy nhiên, các rủi ro đứt gãy chuỗi cung ứng và thiếu hụt điện năng làm phát sinh nhiều thách thức lớn cho các trung tâm dữ liệu.
        Nhiều chuyên gia cảnh báo rằng nếu không có năng lượng xanh, chi phí vận hành sẽ tăng gấp đôi.
        
        Tóm lại, các doanh nghiệp cần đa dạng hóa nguồn cung cấp linh kiện và đầu tư vào giải pháp tản nhiệt lỏng để tối ưu hiệu suất.
    """.trimIndent()

    @Test
    fun generateSuggestedQuestions_englishTechArticle_returnsRelevantChips() {
        val chips = ArticleDeepReadEngine.generateSuggestedQuestions(
            title = techArticleTitle,
            plainText = techArticleBody,
            languageTag = "en",
        )

        assertTrue("Should return 3 to 4 question chips", chips.size in 3..4)
        assertTrue("Should contain takeaways prompt", chips.any { it.contains("takeaways", ignoreCase = true) })
        assertTrue("Should contain technology prompt", chips.any { it.contains("technology", ignoreCase = true) || it.contains("work", ignoreCase = true) })
        assertTrue("Should contain risks prompt", chips.any { it.contains("risk", ignoreCase = true) })
    }

    @Test
    fun generateSuggestedQuestions_vietnameseArticle_returnsVietnameseChips() {
        val chips = ArticleDeepReadEngine.generateSuggestedQuestions(
            title = viArticleTitle,
            plainText = viArticleBody,
            languageTag = "vi",
        )

        assertTrue("Should return 3 to 4 question chips", chips.size in 3..4)
        assertTrue("Should contain Vietnamese summary prompt", chips.any { it.contains("Tóm tắt", ignoreCase = true) })
        assertTrue("Should contain risk or market prompt", chips.any { it.contains("rủi ro", ignoreCase = true) || it.contains("kinh tế", ignoreCase = true) })
    }

    @Test
    fun generateOfflineAnswer_summaryQuestion_returnsTopSalientPoints() {
        val answer = ArticleDeepReadEngine.generateOfflineAnswer(
            title = techArticleTitle,
            plainText = techArticleBody,
            question = "What is the summary of this article?",
            languageTag = "en",
        )

        assertEquals(DeepReadSender.ASSISTANT, answer.sender)
        assertTrue("Should be offline fallback", answer.isOfflineFallback)
        assertTrue("Should be marked grounded", answer.isGrounded)
        assertTrue("Content should have summary bullet format", answer.content.contains("•"))
    }

    @Test
    fun generateOfflineAnswer_riskQuestion_extractsRiskSentences() {
        val answer = ArticleDeepReadEngine.generateOfflineAnswer(
            title = techArticleTitle,
            plainText = techArticleBody,
            question = "What are the main risks and limitations?",
            languageTag = "en",
        )

        assertTrue("Answer should mention security or risks", answer.content.contains("risk", ignoreCase = true) || answer.content.contains("security", ignoreCase = true))
    }

    @Test
    fun generateOfflineAnswer_metricQuestion_extractsDataAndNumbers() {
        val answer = ArticleDeepReadEngine.generateOfflineAnswer(
            title = techArticleTitle,
            plainText = techArticleBody,
            question = "What are the key numbers and percent savings?",
            languageTag = "en",
        )

        assertTrue("Answer should extract metric facts (85% or 12 million)", answer.content.contains("85%") || answer.content.contains("12 million"))
    }

    @Test
    fun generateOfflineAnswer_vietnameseQuestion_returnsVietnamesePrefix() {
        val answer = ArticleDeepReadEngine.generateOfflineAnswer(
            title = viArticleTitle,
            plainText = viArticleBody,
            question = "Các rủi ro chính được đề cập là gì?",
            languageTag = "vi",
        )

        assertTrue("Should contain Vietnamese header", answer.content.contains("Dựa theo nội dung bài viết") || answer.content.contains("rủi ro"))
    }

    @Test
    fun generateOfflineAnswer_emptyContent_handlesGracefully() {
        val answer = ArticleDeepReadEngine.generateOfflineAnswer(
            title = "Empty",
            plainText = "   ",
            question = "Summary please",
            languageTag = "en",
        )

        assertFalse("Should indicate not grounded", answer.isGrounded)
        assertTrue("Content should explain lack of text", answer.content.contains("does not contain enough text"))
    }

    @Test
    fun deepReadSession_formatTranscript_producesReadableHistory() {
        val session = DeepReadSession(
            articleId = "art_101",
            articleTitle = "AI Frontiers",
            messages = listOf(
                DeepReadMessage(
                    sender = DeepReadSender.USER,
                    content = "What is the main topic?"
                ),
                DeepReadMessage(
                    sender = DeepReadSender.ASSISTANT,
                    content = "The article covers autonomous agent architectures.",
                    isOfflineFallback = true
                )
            ),
            suggestedChips = listOf("Chip 1", "Chip 2")
        )

        val transcript = session.formatTranscript()
        assertTrue("Transcript should contain title", transcript.contains("AI Frontiers"))
        assertTrue("Transcript should contain user message", transcript.contains("👤 You"))
        assertTrue("Transcript should contain AI message", transcript.contains("✨ AI Assistant"))
        assertTrue("Transcript should indicate offline status", transcript.contains("[Offline Smart]"))
    }
}
