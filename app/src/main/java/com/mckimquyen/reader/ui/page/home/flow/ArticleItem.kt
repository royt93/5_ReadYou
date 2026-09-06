package com.mckimquyen.reader.ui.page.home.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Precision
import coil.size.Scale
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.infrastructure.pref.LocalAmoledDarkTheme
import com.mckimquyen.reader.infrastructure.pref.LocalDarkTheme
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListDesc
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListFeedIcon
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListFeedName
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListImage
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListTime
import com.mckimquyen.reader.domain.watchdog.WatchdogEngine
import com.mckimquyen.reader.ui.component.FeedIcon
import com.mckimquyen.reader.ui.component.base.BaseAsyncImage
import com.mckimquyen.reader.ui.component.base.SIZE_1000
import com.mckimquyen.reader.ui.component.watchdog.LocalWatchdogKeywords
import com.mckimquyen.reader.ui.component.watchdog.WatchdogBadge
import com.mckimquyen.reader.ui.theme.Shape20

@Composable
fun ArticleItem(
    articleWithFeed: ArticleWithFeed,
    onClick: (ArticleWithFeed) -> Unit = {},
) {
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListFeedName = LocalFlowArticleListFeedName.current
    val articleListImage = LocalFlowArticleListImage.current
    val articleListDesc = LocalFlowArticleListDesc.current
    val articleListDate = LocalFlowArticleListTime.current

    val watchdogKeywords = LocalWatchdogKeywords.current
    val matchedKeyword = remember(articleWithFeed.article.id, watchdogKeywords) {
        if (watchdogKeywords.isEmpty()) null
        else {
            WatchdogEngine.matchArticle(
                title = articleWithFeed.article.title,
                desc = articleWithFeed.article.shortDescription,
                content = articleWithFeed.article.fullContent,
                keywords = watchdogKeywords,
            )
        }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(Shape20)
            .clickable { onClick(articleWithFeed) }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .alpha(if (articleWithFeed.article.isStarred || articleWithFeed.article.isUnread) 1f else 0.5f),
    ) {
        // Top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Feed name
            if (articleListFeedName.value) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (articleListFeedIcon.value) 30.dp else 0.dp),
                    text = articleWithFeed.feed.name,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right
            if (articleListDate.value) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!articleListFeedName.value) {
                        Spacer(Modifier.width(if (articleListFeedIcon.value) 30.dp else 0.dp))
                    }
                    // Starred
                    if (articleWithFeed.article.isStarred) {
                        Icon(
                            modifier = Modifier
                                .alpha(0.7f)
                                .size(14.dp)
                                .padding(end = 2.dp),
                            imageVector = Icons.Rounded.Star,
                            contentDescription = stringResource(R.string.starred),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }

                    // Date
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = articleWithFeed.article.dateString ?: "",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // Bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Feed icon
            if (articleListFeedIcon.value) {
                FeedIcon(articleWithFeed.feed.name, iconUrl = articleWithFeed.feed.icon)
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Article
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // Watchdog Alert Badge
                if (matchedKeyword != null) {
                    WatchdogBadge(
                        keyword = matchedKeyword.keyword,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Title
                Text(
                    text = articleWithFeed.article.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (articleListDesc.value) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                )

                // Description
                if (articleListDesc.value && articleWithFeed.article.shortDescription.isNotBlank()) {
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = articleWithFeed.article.shortDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Image
            if (articleWithFeed.article.img != null && articleListImage.value) {
                BaseAsyncImage(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(80.dp)
                        .clip(Shape20),
                    data = articleWithFeed.article.img,
                    scale = Scale.FILL,
                    precision = Precision.INEXACT,
                    size = SIZE_1000,
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun SwipeToDismiss(
    articleWithFeed: ArticleWithFeed,
    onClick: (ArticleWithFeed) -> Unit = {},
    onSwipeOut: (ArticleWithFeed) -> Unit = {},
) {
    var isArticleVisible by remember { mutableStateOf(true) }
    val dismissState = rememberDismissState(initialValue = DismissValue.Default, confirmStateChange = {
        if (it == DismissValue.DismissedToEnd) {
            isArticleVisible = false
            onSwipeOut(articleWithFeed)
        }
        true
    })
    if (isArticleVisible) {
        SwipeToDismiss(
            state = dismissState,
            /***  create dismiss alert background box */
            background = {
                if (dismissState.dismissDirection == DismissDirection.StartToEnd) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseSurface,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(
                                text = "Mark Read",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.inverseSurface
                            )
                        }

                    }
                }
            },
            /**** Dismiss Content */
            dismissContent = {
                val isDarkTheme = LocalDarkTheme.current.isDarkTheme()
                val isAmoledDarkTheme = LocalAmoledDarkTheme.current.value

                val articleItemBackgroundColor = if (isDarkTheme && isAmoledDarkTheme) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.background
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(articleItemBackgroundColor)
                ) {
                    ArticleItem(articleWithFeed, onClick)
                }
            },
            /*** Set Direction to dismiss */
            directions = setOf(DismissDirection.StartToEnd),
        )
    }
}
