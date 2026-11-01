package com.mckimquyen.reader.domain.model.bounty

import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import java.util.Date

/**
 * Kết quả điều tra thực thể từ kho lưu trữ tin tức Room cục bộ (BOUNTY-01).
 * Không chứa cột body lớn; danh sách bài dùng [ArticleListItemWithFeed] (FIX-17).
 */
data class BountyInvestigationResult(
    val query: String,
    val articles: List<ArticleListItemWithFeed>,
    val sourceCount: Int,
    val earliestDate: Date?,
    val latestDate: Date?,
)
