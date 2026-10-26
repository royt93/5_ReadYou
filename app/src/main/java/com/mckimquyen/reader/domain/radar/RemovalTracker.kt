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

    /** From this many in-window articles on, a majority vanishing at once is a feed reset, not retractions. */
    private const val MASS_ABSENCE_MIN_ARTICLES = 4

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
        val inWindow = stored.filter { !it.removed && it.date != null && it.date >= oldestFeedDate }
        val massAbsence = inWindow.size >= MASS_ABSENCE_MIN_ARTICLES &&
            inWindow.count { it.link !in presentLinks } * 2 > inWindow.size
        return stored.mapNotNull { article ->
            when {
                article.link in presentLinks ->
                    if (article.missedSyncs > 0 || article.removed) RemovalUpdate(article.articleId, 0, false) else null
                massAbsence || article.removed -> null
                article.date == null || article.date < oldestFeedDate -> null
                else -> {
                    val missed = article.missedSyncs + 1
                    RemovalUpdate(article.articleId, missed, missed >= REMOVAL_THRESHOLD)
                }
            }
        }
    }
}
