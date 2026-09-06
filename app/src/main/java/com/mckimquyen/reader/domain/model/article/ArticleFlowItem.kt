package com.mckimquyen.reader.domain.model.article

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import androidx.paging.filter
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.cluster.StoryClusterResult
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper

/**
 * Provide paginated and inserted separator data types for article list view.
 *
 * @see com.mckimquyen.reader.ui.page.home.flow.ArticleList
 */
sealed class ArticleFlowItem {

    /**
     * The [Article] item.
     *
     * @see com.mckimquyen.reader.ui.page.home.flow.ArticleItem
     */
    class Article(val articleWithFeed: ArticleWithFeed) : ArticleFlowItem()

    /**
     * The [StoryCluster] item grouping duplicate/multi-source articles for an event.
     *
     * @see com.mckimquyen.reader.ui.component.cluster.StoryClusterCard
     */
    class Cluster(val cluster: StoryCluster) : ArticleFlowItem()

    /**
     * The feed publication date separator between [Article] items.
     *
     * @see com.mckimquyen.reader.ui.page.home.flow.StickyHeader
     */
    class Date(val date: String, val showSpacer: Boolean) : ArticleFlowItem()
}

/**
 * Mapping [ArticleWithFeed] list to [ArticleFlowItem] list with optional story clustering.
 */
fun PagingData<ArticleWithFeed>.mapPagingFlowItem(
    androidStringsHelper: AndroidStringsHelper,
    clusterResult: StoryClusterResult? = null,
): PagingData<ArticleFlowItem> {
    val filtered = if (clusterResult != null && clusterResult.nonLeadIds.isNotEmpty()) {
        filter { !clusterResult.nonLeadIds.contains(it.article.id) }
    } else {
        this
    }

    return filtered.map { articleWithFeed ->
        val cluster = clusterResult?.leadClusterMap?.get(articleWithFeed.article.id)
        if (cluster != null) {
            cluster.dateString = androidStringsHelper.formatAsString(
                date = cluster.date,
                onlyHourMinute = true
            ) ?: ""
            ArticleFlowItem.Cluster(cluster)
        } else {
            ArticleFlowItem.Article(articleWithFeed.apply {
                article.dateString = androidStringsHelper.formatAsString(
                    date = article.date,
                    onlyHourMinute = true
                )
            })
        }
    }.insertSeparators { before, after ->
        val beforeDate = when (before) {
            is ArticleFlowItem.Article -> androidStringsHelper.formatAsString(before.articleWithFeed.article.date)
            is ArticleFlowItem.Cluster -> androidStringsHelper.formatAsString(before.cluster.date)
            else -> null
        }
        val afterDate = when (after) {
            is ArticleFlowItem.Article -> androidStringsHelper.formatAsString(after.articleWithFeed.article.date)
            is ArticleFlowItem.Cluster -> androidStringsHelper.formatAsString(after.cluster.date)
            else -> null
        }
        if (beforeDate != afterDate) {
            afterDate?.let { ArticleFlowItem.Date(it, beforeDate != null) }
        } else {
            null
        }
    }
}
