package com.mckimquyen.reader.ui.page.home.flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.mckimquyen.reader.domain.model.article.ArticleFlowItem
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.ui.component.cluster.StoryClusterCard

@Suppress("FunctionName")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
fun LazyListScope.ArticleList(
    pagingItems: LazyPagingItems<ArticleFlowItem>,
    isShowFeedIcon: Boolean,
    isShowStickyHeader: Boolean,
    articleListTonalElevation: Int,
    onClick: (ArticleWithFeed) -> Unit = {},
    onClusterClick: (StoryCluster) -> Unit = {},
    onSwipeOut: (ArticleWithFeed) -> Unit = {},
) {
    for (index in 0 until pagingItems.itemCount) {
        when (val item = pagingItems.peek(index)) {
            is ArticleFlowItem.Article -> {
                item(key = item.articleWithFeed.article.id) {
                    SwipeToDismiss(
                        articleWithFeed = (pagingItems[index] as ArticleFlowItem.Article).articleWithFeed,
                        onClick = { onClick(it) },
                        onSwipeOut = { onSwipeOut(it) }
                    )
                }
            }

            is ArticleFlowItem.Cluster -> {
                item(key = item.cluster.id) {
                    StoryClusterCard(
                        cluster = (pagingItems[index] as? ArticleFlowItem.Cluster)?.cluster ?: item.cluster,
                        isShowFeedIcon = isShowFeedIcon,
                        onClick = { onClusterClick(it) },
                        onLeadClick = { onClick(it) },
                    )
                }
            }

            is ArticleFlowItem.Date -> {
                if (item.showSpacer) item { Spacer(modifier = Modifier.height(40.dp)) }
                if (isShowStickyHeader) {
                    stickyHeader(key = item.date) {
                        StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation)
                    }
                } else {
                    item(key = item.date) {
                        StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation)
                    }
                }
            }

            else -> {}
        }
    }
}
