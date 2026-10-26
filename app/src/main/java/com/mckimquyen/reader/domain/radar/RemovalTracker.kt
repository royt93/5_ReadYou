package com.mckimquyen.reader.domain.radar

data class StoredArticleState(
    val articleId: String,
    val link: String,
    val date: Long?,
    val missedSyncs: Int,
    val removed: Boolean,
)

data class RemovalUpdate(val articleId: String, val missedSyncs: Int, val removed: Boolean)

object RemovalTracker {
    /** Consecutive valid syncs an article must be absent from its feed before it is flagged removed. */
    const val REMOVAL_THRESHOLD = 3

    /**
     * @param fetchValid the feed fetch succeeded; a failed fetch must never count as a miss.
     * @param presentLinks links in the fetched feed.
     * @param oldestFeedDate date of the oldest entry still in the feed (null when the feed is empty).
     * Stored articles older than that were merely trimmed off by the feed, so they are never counted.
     */
    fun evaluate(
        fetchValid: Boolean,
        presentLinks: Set<String>,
        oldestFeedDate: Long?,
        stored: List<StoredArticleState>,
    ): List<RemovalUpdate> {
        if (!fetchValid || oldestFeedDate == null || presentLinks.isEmpty()) return emptyList()
        return stored.mapNotNull { article ->
            when {
                article.link in presentLinks ->
                    if (article.missedSyncs > 0 || article.removed) RemovalUpdate(article.articleId, 0, false) else null
                article.removed -> null
                article.date == null || article.date < oldestFeedDate -> null
                else -> {
                    val missed = article.missedSyncs + 1
                    RemovalUpdate(article.articleId, missed, missed >= REMOVAL_THRESHOLD)
                }
            }
        }
    }
}
