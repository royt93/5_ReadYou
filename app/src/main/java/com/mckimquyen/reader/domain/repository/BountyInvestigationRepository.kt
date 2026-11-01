package com.mckimquyen.reader.domain.repository

import com.mckimquyen.reader.domain.model.bounty.BountyInvestigationResult
import com.mckimquyen.reader.domain.model.bounty.BountyQuerySanitizer
import javax.inject.Inject

/**
 * Điều tra thực thể (BOUNTY-01) trên metadata bài viết cùng tài khoản trong Room.
 * Chỉ đọc title/shortDescription (FIX-17), không gọi mạng và không cần API key.
 * Repository tự escape ký tự đặc biệt của SQL LIKE nên nơi gọi truyền chuỗi đã chuẩn hoá thô.
 */
class BountyInvestigationRepository @Inject constructor(
    private val articleDao: ArticleDao,
) {

    suspend fun investigate(accountId: Int, currentArticleId: String, query: String): BountyInvestigationResult {
        val articles = articleDao.queryRelatedArticleListWithFeed(
            accountId = accountId,
            currentArticleId = currentArticleId,
            escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral(query),
            limit = MAX_RESULTS,
        )
        val dates = articles.map { it.article.date }
        return BountyInvestigationResult(
            query = query,
            articles = articles,
            sourceCount = articles.map { it.article.feedId }.distinct().size,
            earliestDate = dates.minOrNull(),
            latestDate = dates.maxOrNull(),
        )
    }

    companion object {
        const val MAX_RESULTS = 20
    }
}
