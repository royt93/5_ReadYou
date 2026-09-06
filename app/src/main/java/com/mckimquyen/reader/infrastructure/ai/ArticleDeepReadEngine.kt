package com.mckimquyen.reader.infrastructure.ai

import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import java.util.Locale

/**
 * Heuristic engine for AI Deep Read:
 * 1. Generates context-aware suggested prompt questions based on article content and language.
 * 2. Provides grounded offline question-answering when network or Gemini API is unreachable.
 */
object ArticleDeepReadEngine {

    private val STOP_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "if", "because", "as", "what",
        "which", "this", "that", "these", "those", "then", "just", "so", "than",
        "such", "both", "through", "about", "for", "is", "of", "while", "during",
        "to", "from", "in", "out", "on", "off", "again", "further", "then", "once",
        "và", "hoặc", "nhưng", "nếu", "vì", "như", "gì", "nào", "này", "đó",
        "thì", "được", "là", "của", "cho", "với", "trong", "ngoài", "trên", "dưới",
        "các", "những", "một", "hai", "ba", "có", "không", "khi", "lúc", "về",
        "tại", "theo", "đến", "từ", "ra", "vào", "lại", "rất", "quá", "lắm"
    )

    /**
     * Generates 3-4 insightful question chips tailored to the article's topic.
     */
    fun generateSuggestedQuestions(
        title: String,
        plainText: String,
        languageTag: String = "en"
    ): List<String> {
        val isVietnamese = languageTag.startsWith("vi", ignoreCase = true)
        val textLower = (title + " " + plainText.take(500)).lowercase(Locale.ROOT)

        val questions = mutableListOf<String>()

        if (isVietnamese) {
            questions.add("Tóm tắt ý chính của bài viết trong 3 câu?")
            if (textLower.contains("công nghệ") || textLower.contains("ai") || textLower.contains("tech") || textLower.contains("phần mềm")) {
                questions.add("Công nghệ này hoạt động như thế nào và có ưu điểm gì?")
            } else if (textLower.contains("kinh tế") || textLower.contains("thị trường") || textLower.contains("doanh nghiệp") || textLower.contains("giá")) {
                questions.add("Tác động kinh tế và tài chính được nêu là gì?")
            } else {
                questions.add("Điểm cốt lõi tác giả muốn truyền đạt là gì?")
            }
            questions.add("Có rủi ro, hạn chế hay thách thức nào không?")
            questions.add("Bài viết đưa ra kết luận hoặc khuyến nghị gì?")
        } else {
            questions.add("What are the key takeaways in 3 points?")
            if (textLower.contains("ai") || textLower.contains("tech") || textLower.contains("software") || textLower.contains("model")) {
                questions.add("How does this technology work and why does it matter?")
            } else if (textLower.contains("market") || textLower.contains("economy") || textLower.contains("business") || textLower.contains("price")) {
                questions.add("What are the economic and financial implications?")
            } else {
                questions.add("What is the central argument of the author?")
            }
            questions.add("What are the main risks or limitations discussed?")
            questions.add("What actions or next steps are recommended?")
        }

        return questions.take(4)
    }

    /**
     * Generates a grounded, extractive answer from the article body when offline.
     */
    fun generateOfflineAnswer(
        title: String,
        plainText: String,
        question: String,
        languageTag: String = "en"
    ): DeepReadMessage {
        val isVietnamese = languageTag.startsWith("vi", ignoreCase = true)
        val cleaned = plainText.trim()
        if (cleaned.isBlank()) {
            val emptyMsg = if (isVietnamese) {
                "Không có đủ nội dung bài viết để phân tích và trả lời."
            } else {
                "The article does not contain enough text content to generate an answer."
            }
            return DeepReadMessage(
                sender = DeepReadSender.ASSISTANT,
                content = emptyMsg,
                isOfflineFallback = true,
                isGrounded = false,
            )
        }

        val sentences = cleaned
            .split(Regex("(?<=[.!?])\\s+|\n+"))
            .map { it.trim() }
            .filter { it.length > 25 }

        if (sentences.isEmpty()) {
            return DeepReadMessage(
                sender = DeepReadSender.ASSISTANT,
                content = cleaned.take(300),
                isOfflineFallback = true,
                isGrounded = true,
            )
        }

        val questionTokens = question
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }

        val qLower = question.lowercase(Locale.ROOT)
        val isAskingSummary = qLower.contains("tóm tắt") || qLower.contains("summary") || qLower.contains("ý chính") || qLower.contains("takeaway")
        val isAskingRisk = qLower.contains("rủi ro") || qLower.contains("hạn chế") || qLower.contains("thách thức") || qLower.contains("risk") || qLower.contains("limitation")
        val isAskingMetric = qLower.contains("số liệu") || qLower.contains("kết quả") || qLower.contains("phần trăm") || qLower.contains("percent") || qLower.contains("metric")

        val scoredSentences = sentences.mapIndexed { index, sentence ->
            var score = 0.0
            val sLower = sentence.lowercase(Locale.ROOT)

            // Keyword match score
            for (token in questionTokens) {
                if (sLower.contains(token)) {
                    score += 3.0
                }
            }

            // Lead sentence heuristic bias
            if (index == 0) score += 2.5
            if (index == 1) score += 1.5

            // Intent bonus
            if (isAskingRisk && (sLower.contains("rủi ro") || sLower.contains("risk") || sLower.contains("challenge") || sLower.contains("hạn chế") || sLower.contains("khó khăn"))) {
                score += 5.0
            }
            if (isAskingMetric && Regex("\\d+%|\\$\\d+|\\d+\\s*(triệu|tỷ|million|billion)").containsMatchIn(sLower)) {
                score += 4.0
            }
            if (isAskingSummary && (sLower.contains("kết luận") || sLower.contains("conclude") || sLower.contains("tổng quan") || index < 3)) {
                score += 3.0
            }

            sentence to score
        }

        val topMatches = scoredSentences
            .sortedByDescending { it.second }
            .take(if (isAskingSummary) 3 else 2)
            .map { it.first }

        val resultText = buildString {
            if (isAskingSummary) {
                appendLine(if (isVietnamese) "Dưới đây là các điểm mấu chốt được trích xuất từ bài viết:" else "Here are the key takeaways extracted from the article:")
                appendLine()
                topMatches.forEach { s ->
                    appendLine("• $s")
                }
            } else if (topMatches.isNotEmpty() && scoredSentences.maxOfOrNull { it.second } ?: 0.0 > 1.0) {
                appendLine(if (isVietnamese) "Dựa theo nội dung bài viết:" else "Based on the article context:")
                appendLine()
                topMatches.forEach { s ->
                    appendLine(s)
                }
            } else {
                appendLine(if (isVietnamese) "Thông tin liên quan nhất tìm thấy trong bài viết:" else "Most relevant information found in the article:")
                appendLine()
                sentences.take(2).forEach { s ->
                    appendLine("• $s")
                }
            }
        }.trim()

        return DeepReadMessage(
            sender = DeepReadSender.ASSISTANT,
            content = resultText,
            isOfflineFallback = true,
            isGrounded = true,
        )
    }
}
