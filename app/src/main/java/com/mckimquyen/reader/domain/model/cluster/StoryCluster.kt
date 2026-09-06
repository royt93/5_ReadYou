package com.mckimquyen.reader.domain.model.cluster

import androidx.annotation.Keep
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import java.util.Date

/**
 * Cụm sự kiện / tin tức gom nhiều bài báo cùng chủ đề từ các nguồn khác nhau.
 * Giải quyết nạn ngập lụt tin tức trùng lặp trên bảng tin FlowPage.
 */
@Keep
data class StoryCluster(
    val id: String,
    val title: String,
    val leadArticle: ArticleWithFeed,
    val articles: List<ArticleWithFeed>,
    val keywords: List<String> = emptyList(),
    val sourceCount: Int = articles.map { it.feed.id }.distinct().size,
    val articleCount: Int = articles.size,
    val date: Date = articles.maxOfOrNull { it.article.date } ?: Date(),
    var dateString: String = "",
    val summary: String? = null,
    val similarityScore: Float = 0.85f,
) {
    val isMultiSource: Boolean get() = sourceCount > 1
}

/**
 * Kết quả phân cụm tin tức thời sự.
 */
@Keep
data class StoryClusterResult(
    val clusters: List<StoryCluster> = emptyList(),
    val leadClusterMap: Map<String, StoryCluster> = emptyMap(),
    val nonLeadIds: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = StoryClusterResult()
    }
}
