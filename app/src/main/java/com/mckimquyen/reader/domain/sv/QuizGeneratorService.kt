package com.mckimquyen.reader.domain.sv

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



    companion object {

        const val CATEGORY_TECH = "Tech & AI"
        const val CATEGORY_BUSINESS = "Business & Finance"
        const val CATEGORY_SCIENCE = "Science & Nature"
        const val CATEGORY_HEALTH = "Health & Lifestyle"
        const val CATEGORY_PHILOSOPHY = "Philosophy & Ideas"
        const val CATEGORY_GENERAL = "General Knowledge"
    }
}
