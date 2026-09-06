package com.mckimquyen.reader.domain.watchdog

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.watchdog.WatchdogKeyword
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Động cơ so khớp từ khóa cảnh báo khẩn cấp (Keyword Watchdog Matching Engine).
 * Quét tiêu đề, mô tả và nội dung bài viết để tìm từ khóa theo dõi (hỗ trợ mã chứng khoán $XYZ, cụm từ tiếng Việt/Anh).
 */
@Singleton
class WatchdogEngine @Inject constructor() {

    /**
     * Kiểm tra xem bài viết có chứa bất kỳ từ khóa nào đang được kích hoạt hay không.
     * Trả về WatchdogKeyword đầu tiên khớp được, hoặc null nếu không có.
     */
    fun match(article: Article, keywords: List<WatchdogKeyword>): WatchdogKeyword? {
        return matchArticle(article.title, article.shortDescription, article.fullContent, keywords)
    }

    companion object {
        /**
         * Kiểm tra xem văn bản bài viết có chứa bất kỳ từ khóa nào đang bật hay không.
         */
        fun matchArticle(
            title: String,
            desc: String,
            content: String?,
            keywords: List<WatchdogKeyword>
        ): WatchdogKeyword? {
            val activeKeywords = keywords.filter { it.isEnabled && it.keyword.isNotBlank() }
            if (activeKeywords.isEmpty()) return null

            val lowerTitle = title.lowercase(Locale.ROOT)
            val lowerDesc = desc.lowercase(Locale.ROOT)
            val lowerContent = (content ?: "").lowercase(Locale.ROOT)

            for (kw in activeKeywords) {
                val rawKw = kw.keyword.trim()
                val cleanKw = rawKw.lowercase(Locale.ROOT)

                if (matchesText(cleanKw, lowerTitle) ||
                    matchesText(cleanKw, lowerDesc) ||
                    (lowerContent.isNotBlank() && matchesText(cleanKw, lowerContent))
                ) {
                    return kw
                }
            }
            return null
        }

        /**
         * So khớp từ khóa với văn bản: hỗ trợ mã $TICKER và cụm từ tiếng Việt / quốc tế.
         */
        fun matchesText(keyword: String, text: String): Boolean {
            if (keyword.isBlank() || text.isBlank()) return false
            val cleanKw = keyword.trim().lowercase(Locale.ROOT)
            val lowerText = text.lowercase(Locale.ROOT)

            // 1. Mã cổ phiếu có tiền tố $ (vd: $VIC, $FPT, $NVDA)
            if (cleanKw.startsWith("$") && cleanKw.length > 1) {
                val ticker = cleanKw.removePrefix("$")
                val regexWithDollar = Regex("(?:^|\\s)\\$${Regex.escape(ticker)}(?:$|\\s|[.,!?;:])")
                val regexWithoutDollar = Regex("\\b${Regex.escape(ticker)}\\b")
                return regexWithDollar.containsMatchIn(lowerText) || regexWithoutDollar.containsMatchIn(lowerText)
            }

            // 2. Cụm từ thông thường: so khớp biên từ hoặc chứa chuỗi
            return if (cleanKw.length <= 4) {
                // Từ ngắn: yêu cầu khớp biên từ để tránh false positive (vd: "vàng" không bị khớp sai trong các từ ghép vô nghĩa)
                val regex = Regex("(?:^|[^\\p{L}\\p{Nd}])${Regex.escape(cleanKw)}(?:$|[^\\p{L}\\p{Nd}])")
                regex.containsMatchIn(lowerText)
            } else {
                lowerText.contains(cleanKw)
            }
        }
    }
}
