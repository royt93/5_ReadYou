package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Thuật toán chọn lọc nội dung cho đài phát thanh CommuteCast (DJ-01 & DJ-08):
 * 1. Tính "điểm tương tác" (Interaction Score) dựa trên độ mới, cờ yêu thích (Star), cờ đọc sau, và độ phong phú tóm tắt.
 * 2. Ước lượng thời lượng đọc/phát âm theo số từ và tốc độ nói chuẩn (~150 từ/phút).
 * 3. Lựa chọn bài viết khớp với ngân sách thời gian (Time Budget) đã định.
 * 4. Đảm bảo tính đa dạng nguồn tin (Feed Diversity): không quá N bài liên tiếp từ cùng một feedId.
 */
@Singleton
class CommuteContentSelector @Inject constructor() {

    companion object {
        const val DEFAULT_STANDARD_BUDGET_MINUTES = 4
        const val DEFAULT_DEEP_DIVE_BUDGET_MINUTES = 15

        const val WORDS_PER_MINUTE = 150
        const val INTRO_OUTRO_SECONDS = 35
        const val PER_ARTICLE_OVERHEAD_SECONDS = 15
        const val MAX_CONSECUTIVE_PER_FEED = 2

        const val MAX_CANDIDATES_QUERY_LIMIT = 50

        const val MIN_ARTICLE_DURATION_SECONDS = 20
        const val MAX_ARTICLE_DURATION_SECONDS = 60

        const val MAX_STANDARD_ARTICLES = 8
        const val MAX_DEEP_DIVE_ARTICLES = 15
    }

    /**
     * Tính điểm tương tác cho bài viết.
     * Điểm cao nhất ưu tiên bài người dùng đã chủ động tương tác (Starred, ReadLater) kết hợp với độ mới.
     */
    fun calculateInteractionScore(article: Article, now: Date = Date()): Float {
        val ageHours = maxOf(0L, now.time - article.date.time) / (1000f * 3600f)
        // Độ mới phân rã theo hàm hyperbolic: 0h = 100đ, 12h = 50đ, 24h = 33đ, 48h = 20đ
        val recencyScore = 100f / (1f + ageHours / 12f)

        // Tín hiệu người dùng chủ động tương tác
        val starredBonus = if (article.isStarred) 50f else 0f
        val readLaterBonus = if (article.isReadLater) 30f else 0f

        // Độ phong phú nội dung (có tóm tắt hoặc nội dung chi tiết)
        val hasGoodDescription = article.shortDescription.trim().length >= 100
        val richnessBonus = (if (hasGoodDescription) 10f else 0f) + (if (!article.aiSummary.isNullOrBlank()) 15f else 0f)

        return recencyScore + starredBonus + readLaterBonus + richnessBonus
    }

    /**
     * Ước lượng thời lượng phát thanh bằng lời thoại cho một bài viết (tính bằng giây).
     * Bao gồm thời gian MC đọc tóm tắt (~150 từ/phút) cộng với thời gian đối đáp chuyển tiếp.
     */
    fun estimateSpokenDurationSeconds(article: Article): Int {
        val snippet = (article.title + " " + article.shortDescription.take(250)).trim()
        val wordCount = snippet.split("\\s+".toRegex()).count { it.isNotBlank() }
        val spokenSeconds = (wordCount / (WORDS_PER_MINUTE / 60f)).roundToInt() + PER_ARTICLE_OVERHEAD_SECONDS
        return spokenSeconds.coerceIn(MIN_ARTICLE_DURATION_SECONDS, MAX_ARTICLE_DURATION_SECONDS)
    }

    /**
     * Lựa chọn danh sách bài viết từ các bài ứng viên (candidates), tối ưu theo ngân sách thời gian
     * và phân bổ đa dạng nguồn tin.
     */
    fun selectArticles(
        candidates: List<Article>,
        targetMinutes: Int = DEFAULT_STANDARD_BUDGET_MINUTES,
        maxConsecutivePerFeed: Int = MAX_CONSECUTIVE_PER_FEED,
        now: Date = Date(),
    ): List<Article> {
        if (candidates.isEmpty()) return emptyList()

        // Loại bỏ bài trùng lặp id
        val distinctCandidates = candidates.distinctBy { it.id }

        // Sắp xếp theo điểm tương tác giảm dần
        val scoredCandidates = distinctCandidates
            .map { it to calculateInteractionScore(it, now) }
            .sortedByDescending { it.second }
            .map { it.first }
            .toMutableList()

        val targetSeconds = maxOf(60, targetMinutes * 60)
        val availableBudgetSeconds = targetSeconds - INTRO_OUTRO_SECONDS
        val maxArticlesCap = if (targetMinutes >= DEFAULT_DEEP_DIVE_BUDGET_MINUTES) {
            MAX_DEEP_DIVE_ARTICLES
        } else {
            MAX_STANDARD_ARTICLES
        }

        val selected = mutableListOf<Article>()
        var accumulatedSeconds = 0
        var lastFeedId: String? = null
        var consecutiveFeedCount = 0

        while (scoredCandidates.isNotEmpty() && accumulatedSeconds < availableBudgetSeconds) {
            // Tìm bài ứng viên điểm cao nhất không vi phạm giới hạn liên tiếp cùng một feed
            val nextArticle = scoredCandidates.firstOrNull { article ->
                if (article.feedId == lastFeedId) {
                    consecutiveFeedCount < maxConsecutivePerFeed
                } else {
                    true
                }
            } ?: scoredCandidates.first() // Fallback: nếu toàn bộ ứng viên còn lại đều từ cùng 1 feed

            selected.add(nextArticle)
            scoredCandidates.remove(nextArticle)

            if (nextArticle.feedId == lastFeedId) {
                consecutiveFeedCount++
            } else {
                lastFeedId = nextArticle.feedId
                consecutiveFeedCount = 1
            }

            accumulatedSeconds += estimateSpokenDurationSeconds(nextArticle)

            if (selected.size >= maxArticlesCap) break
        }

        // Đảm bảo nếu có ứng viên thì phải chọn được ít nhất 1 bài
        if (selected.isEmpty() && distinctCandidates.isNotEmpty()) {
            selected.add(distinctCandidates.first())
        }

        return selected
    }
}
