package com.mckimquyen.reader.domain.watchdog

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.watchdog.WatchdogKeyword
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
        // Only the title/description and this many leading characters of fullContent are scanned:
        // the signal a user cares about is virtually always near the top, and lowercasing/scanning
        // a full-text article on every single sync match is wasted CPU for negligible recall gain.
        private const val MAX_CONTENT_SCAN_CHARS = 2000

        // Compiling a keyword's matcher (Regex or plain substring check) is pure — it only depends
        // on the keyword's own text — so it is cached once per unique keyword string and reused for
        // every article in every sync batch (and beyond), instead of rebuilding it per (keyword, field,
        // article) combination as before. Safe to share across threads: entries are immutable once built.
        private val matcherCache = ConcurrentHashMap<String, KeywordMatcher>()

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
            val lowerContent = content
                ?.take(MAX_CONTENT_SCAN_CHARS)
                ?.lowercase(Locale.ROOT)
                .orEmpty()

            for (kw in activeKeywords) {
                val cleanKw = kw.keyword.trim().lowercase(Locale.ROOT)
                val matcher = matcherFor(cleanKw)

                if (matcher.matches(lowerTitle) ||
                    matcher.matches(lowerDesc) ||
                    (lowerContent.isNotBlank() && matcher.matches(lowerContent))
                ) {
                    return kw
                }
            }
            return null
        }

        /**
         * So khớp từ khóa với văn bản: hỗ trợ mã $TICKER và cụm từ tiếng Việt / quốc tế.
         * Kept as the stable, pattern-agnostic public entry point; internally delegates to the
         * cached [KeywordMatcher] so repeated calls with the same keyword never recompile a Regex.
         */
        fun matchesText(keyword: String, text: String): Boolean {
            if (keyword.isBlank() || text.isBlank()) return false
            val cleanKw = keyword.trim().lowercase(Locale.ROOT)
            return matcherFor(cleanKw).matches(text.lowercase(Locale.ROOT))
        }

        private fun matcherFor(cleanKw: String): KeywordMatcher =
            matcherCache.getOrPut(cleanKw) { KeywordMatcher.compile(cleanKw) }
    }

    /** Precompiled, immutable matching strategy for one already-lowercased keyword. */
    private sealed class KeywordMatcher {
        abstract fun matches(lowerText: String): Boolean

        /** $TICKER: matches either "$VIC" or the bare word "VIC". */
        class Ticker(private val withDollar: Regex, private val withoutDollar: Regex) : KeywordMatcher() {
            override fun matches(lowerText: String): Boolean =
                withDollar.containsMatchIn(lowerText) || withoutDollar.containsMatchIn(lowerText)
        }

        /** Short word (<=4 chars): requires word-boundaries to avoid false positives inside longer words. */
        class WordBoundary(private val regex: Regex) : KeywordMatcher() {
            override fun matches(lowerText: String): Boolean = regex.containsMatchIn(lowerText)
        }

        /** Longer phrase: plain substring search, no Regex needed. */
        class Substring(private val needle: String) : KeywordMatcher() {
            override fun matches(lowerText: String): Boolean = lowerText.contains(needle)
        }

        companion object {
            private const val SHORT_KEYWORD_MAX_LENGTH = 4

            fun compile(cleanKw: String): KeywordMatcher {
                if (cleanKw.startsWith("$") && cleanKw.length > 1) {
                    val ticker = cleanKw.removePrefix("$")
                    return Ticker(
                        withDollar = Regex("(?:^|\\s)\\$${Regex.escape(ticker)}(?:$|\\s|[.,!?;:])"),
                        withoutDollar = Regex("\\b${Regex.escape(ticker)}\\b"),
                    )
                }
                return if (cleanKw.length <= SHORT_KEYWORD_MAX_LENGTH) {
                    WordBoundary(Regex("(?:^|[^\\p{L}\\p{Nd}])${Regex.escape(cleanKw)}(?:$|[^\\p{L}\\p{Nd}])"))
                } else {
                    Substring(cleanKw)
                }
            }
        }
    }
}
