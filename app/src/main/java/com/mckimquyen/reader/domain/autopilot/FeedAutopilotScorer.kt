package com.mckimquyen.reader.domain.autopilot

import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.pref.KeepArchivedPreference

/**
 * Pure scoring of "rarely read" feeds from engagement counts. It never reads the database, the clock or Android
 * classes, and it never builds display text: the screen formats the raw counts from localized resources.
 *
 * Honest limits: the app stores no reading time and no swipe-skip event, so "engagement" is only
 * readAt (opened in Reader), starred or read-later.
 */
object FeedAutopilotScorer {

    const val MIN_ARTICLES = 20
    const val MIN_FEED_AGE_DAYS = 14
    const val MAX_WINDOW_DAYS = 30
    const val MIN_RETENTION_DAYS = 14

    /** Engagement strictly below 1/ENGAGEMENT_DENOMINATOR (5%) is noisy; integer math avoids float rounding at 5%. */
    private const val ENGAGEMENT_DENOMINATOR = 20
    private const val PERCENT = 100
    private const val DAY_MILLIS = 86_400_000L
    private const val SNOOZE_MILLIS = 30L * DAY_MILLIS

    /** 2026-10-18T00:00Z, the day Article.readAt was introduced (commit b46a6bc9). Earlier articles have no readAt. */
    const val READ_AT_INTRODUCED_MILLIS = 1_792_281_600_000L

    /**
     * Start of the evaluation window. Articles that were read and not starred are purged after
     * `keepArchived`, so a longer window would see only unread articles and wrongly look unread.
     */
    fun windowStartMillis(keepArchived: KeepArchivedPreference, nowMillis: Long): Long {
        val windowMillis = minOf(MAX_WINDOW_DAYS * DAY_MILLIS, retentionMillis(keepArchived))
        return maxOf(nowMillis - windowMillis, READ_AT_INTRODUCED_MILLIS)
    }

    fun evaluate(
        stats: List<FeedEngagementStat>,
        feeds: List<Feed>,
        decisions: Map<String, FeedDecisionRecord>,
        keepArchived: KeepArchivedPreference,
        nowMillis: Long,
    ): FeedAutopilotEvaluation {
        if (retentionMillis(keepArchived) < MIN_RETENTION_DAYS * DAY_MILLIS) return FeedAutopilotEvaluation.RetentionTooShort

        val windowDays = ((nowMillis - windowStartMillis(keepArchived, nowMillis)) / DAY_MILLIS).toInt()
        val feedsById = feeds.associateBy { it.id }
        val suggestions = stats.mapNotNull { stat ->
            val feed = feedsById[stat.feedId] ?: return@mapNotNull null
            if (isSuppressed(decisions[stat.feedId], nowMillis)) return@mapNotNull null
            if (stat.windowTotal < MIN_ARTICLES) return@mapNotNull null
            val oldest = stat.oldestUpdateAtMillis ?: return@mapNotNull null
            val feedAgeDays = ((nowMillis - oldest) / DAY_MILLIS).toInt()
            if (feedAgeDays < MIN_FEED_AGE_DAYS) return@mapNotNull null
            if (stat.windowEngaged * ENGAGEMENT_DENOMINATOR >= stat.windowTotal) return@mapNotNull null

            FeedAutopilotSuggestion(
                feedId = feed.id,
                feedName = feed.name,
                feedIcon = feed.icon,
                windowTotal = stat.windowTotal,
                windowEngaged = stat.windowEngaged,
                engagementRatio = stat.windowEngaged.toFloat() / stat.windowTotal,
                engagementPercent = stat.windowEngaged * PERCENT / stat.windowTotal,
                windowDays = windowDays,
                feedAgeDays = feedAgeDays,
            )
        }.sortedWith(
            compareBy<FeedAutopilotSuggestion> { it.engagementRatio }
                .thenByDescending { it.windowTotal }
                .thenBy { it.feedName },
        )
        return FeedAutopilotEvaluation.Ready(suggestions, windowDays)
    }

    /** `Always` keeps everything, which for window purposes is the same as the 30-day maximum, never zero. */
    private fun retentionMillis(keepArchived: KeepArchivedPreference): Long =
        if (keepArchived == KeepArchivedPreference.Always) MAX_WINDOW_DAYS * DAY_MILLIS else keepArchived.value

    private fun isSuppressed(decision: FeedDecisionRecord?, nowMillis: Long): Boolean = when (decision?.type) {
        null -> false
        FeedDecisionType.KEEP_FOREVER -> true
        FeedDecisionType.SNOOZE_30_DAYS -> nowMillis - decision.decidedAtMillis < SNOOZE_MILLIS
    }
}
