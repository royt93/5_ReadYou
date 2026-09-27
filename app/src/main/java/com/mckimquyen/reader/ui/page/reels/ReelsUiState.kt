package com.mckimquyen.reader.ui.page.reels

import androidx.annotation.Keep
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed

/** One full-screen card in the reels pager. */
@Keep
data class ReelItem(
    val articleId: String,
    val title: String,
    val feedName: String,
    val publishedAtMillis: Long,
    val coverImageUrl: String?,
    /** Offline summary bullets, at most [ReelsViewModel.BULLET_COUNT]. */
    val bullets: List<String>,
    val hasVideo: Boolean,
)

@Keep
data class ReelsUiState(
    val items: List<ReelItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** True once loading finished and there is genuinely nothing to show. */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * Builds a card from a stored article. Kept outside the ViewModel so the mapping — including the
 * summary bullets — can be unit-tested without Android or Hilt.
 */
fun ArticleWithFeed.toReelItem(bullets: List<String>, hasVideo: Boolean): ReelItem = ReelItem(
    articleId = article.id,
    title = article.title,
    feedName = feed.name,
    publishedAtMillis = article.date.time,
    coverImageUrl = article.img,
    bullets = bullets,
    hasVideo = hasVideo,
)
