package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizGeneratorService @Inject constructor() {

    fun detectCategory(title: String, content: String): String {
        val combined = "$title $content".lowercase()
        return when {
            combined.matchesKeywords("ai", "trí tuệ nhân tạo", "google", "apple", "microsoft", "code", "software", "chip", "tech", "nvidia", "robot", "phần mềm", "lập trình", "công nghệ") ->
                CATEGORY_TECH
            combined.matchesKeywords("kinh tế", "cổ phiếu", "lạm phát", "fed", "thị trường", "tài chính", "startup", "doanh thu", "business", "market", "finance", "stock", "crypto", "bitcoin", "ngân hàng") ->
                CATEGORY_BUSINESS
            combined.matchesKeywords("khoa học", "vũ trụ", "nasa", "sinh học", "vật lý", "khí hậu", "nghiên cứu", "science", "space", "biology", "physics", "earth", "climate", "năng lượng") ->
                CATEGORY_SCIENCE
            combined.matchesKeywords("sức khỏe", "thể thao", "dinh dưỡng", "y tế", "bác sĩ", "tập luyện", "giấc ngủ", "health", "diet", "fitness", "sleep", "medicine", "wellness") ->
                CATEGORY_HEALTH
            combined.matchesKeywords("triết học", "tư duy", "lịch sử", "văn hóa", "đạo đức", "sách", "philosophy", "thought", "history", "culture", "ethics", "book") ->
                CATEGORY_PHILOSOPHY
            else -> CATEGORY_GENERAL
        }
    }

    fun generateQuiz(articleId: String, title: String, content: String, author: String? = null): QuizQuestion {
        val cleanTitle = title.trim().replace("\n", " ")
        val cleanContent = content.replace(Regex("<[^>]*>"), " ").trim()
        val category = detectCategory(cleanTitle, cleanContent)
        val rng = Random(articleId.hashCode().toLong())

        // Extract key sentence or clause from cleanContent
        val sentences = cleanContent.split(Regex("[.!?]\\s+")).filter { it.length > 20 && it.length < 150 }
        val keySentence = sentences.firstOrNull()?.trim()

        val isVietnamese = cleanTitle.containsAny("và", "các", "của", "trong", "được", "người", "những", "cho", "với", "không", "này")

        val questionText: String
        val correctOption: String
        val distractors: List<String>
        val explanation: String

        if (isVietnamese) {
            val qType = rng.nextInt(3)
            when (qType) {
                0 -> {
                    questionText = "Chủ đề cốt lõi được đề cập trong bài viết là gì?"
                    correctOption = cleanTitle.take(65) + if (cleanTitle.length > 65) "..." else ""
                    distractors = listOf(
                        "Kế hoạch sáp nhập tài chính toàn cầu năm 2030",
                        "Phân tích tác động của biến đổi khí hậu tại vùng cực",
                        "Lịch sử phát triển các nền tảng thương mại truyền thống"
                    )
                    explanation = "Bài viết tập trung phân tích về: \"${cleanTitle.take(80)}\"."
                }
                1 -> {
                    questionText = "Theo nội dung bài viết, thông điệp trọng tâm nào là chính xác?"
                    correctOption = if (!keySentence.isNullOrBlank()) {
                        keySentence.take(65) + if (keySentence.length > 65) "..." else ""
                    } else {
                        "Phát triển và cập nhật quan trọng liên quan đến: ${cleanTitle.take(40)}"
                    }
                    distractors = listOf(
                        "Dự án đã bị hủy bỏ hoàn toàn do thiếu hụt ngân sách",
                        "Chính sách bị tạm ngưng vô thời hạn bởi các cơ quan giám sát",
                        "Không có sự thay đổi hay tác động nào được ghi nhận"
                    )
                    explanation = "Nội dung chính nhấn mạnh diễn biến: \"${cleanTitle.take(80)}\"."
                }
                else -> {
                    questionText = "Mục tiêu hoặc xu hướng nào được bài viết làm nổi bật?"
                    correctOption = "Xu hướng đổi mới và thúc đẩy trong lĩnh vực $category"
                    distractors = listOf(
                        "Cắt giảm toàn bộ chi phí nghiên cứu và phát triển",
                        "Chuyển dịch 100% sang mô hình thủ công truyền thống",
                        "Rút lui hoàn toàn khỏi thị trường quốc tế"
                    )
                    explanation = "Bài viết thuộc lĩnh vực $category với trọng tâm là sự đổi mới và tiến bộ liên tục."
                }
            }
        } else {
            val qType = rng.nextInt(3)
            when (qType) {
                0 -> {
                    questionText = "What is the primary topic discussed in this article?"
                    correctOption = cleanTitle.take(65) + if (cleanTitle.length > 65) "..." else ""
                    distractors = listOf(
                        "Global financial restructuring plans for 2030",
                        "Long-term impact of extreme weather in Antarctica",
                        "Historical evolution of 19th-century maritime commerce"
                    )
                    explanation = "The article primarily focuses on: \"${cleanTitle.take(80)}\"."
                }
                1 -> {
                    questionText = "According to the article, which key insight is highlighted?"
                    correctOption = if (!keySentence.isNullOrBlank()) {
                        keySentence.take(65) + if (keySentence.length > 65) "..." else ""
                    } else {
                        "Significant development regarding ${cleanTitle.take(40)}"
                    }
                    distractors = listOf(
                        "The initiative was canceled indefinitely due to budget cuts",
                        "Regulators decided to halt all operations effective immediately",
                        "No measurable impact or change was observed"
                    )
                    explanation = "Key development summarized in: \"${cleanTitle.take(80)}\"."
                }
                else -> {
                    questionText = "Which direction or strategic focus is emphasized?"
                    correctOption = "Advancements and transformations in $category"
                    distractors = listOf(
                        "Complete divestment from modern research and tools",
                        "Transition back to manual, analog methodologies",
                        "Immediate shutdown of international expansions"
                    )
                    explanation = "The article underscores ongoing advancements within the $category domain."
                }
            }
        }

        // Shuffle options and find index of correct option
        val allOptions = (listOf(correctOption) + distractors.shuffled(rng)).shuffled(rng)
        val correctIndex = allOptions.indexOf(correctOption)

        return QuizQuestion(
            articleId = articleId,
            question = questionText,
            options = allOptions,
            correctAnswerIndex = correctIndex,
            explanation = explanation,
            category = category
        )
    }

    private fun String.matchesKeywords(vararg keywords: String): Boolean {
        val lower = this.lowercase()
        val tokens = lower.split(Regex("[^\\p{L}\\p{Nd}]+")).toSet()
        return keywords.any { kw ->
            if (kw.contains(" ")) {
                lower.contains(kw.lowercase())
            } else if (kw.length <= 4) {
                kw.lowercase() in tokens
            } else {
                lower.contains(kw.lowercase())
            }
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }

    companion object {
        const val CATEGORY_TECH = "Tech & AI"
        const val CATEGORY_BUSINESS = "Business & Finance"
        const val CATEGORY_SCIENCE = "Science & Nature"
        const val CATEGORY_HEALTH = "Health & Lifestyle"
        const val CATEGORY_PHILOSOPHY = "Philosophy & Ideas"
        const val CATEGORY_GENERAL = "General Knowledge"
    }
}
