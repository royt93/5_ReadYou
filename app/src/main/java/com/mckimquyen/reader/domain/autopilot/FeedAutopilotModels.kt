package com.mckimquyen.reader.domain.autopilot

import com.mckimquyen.reader.domain.model.feed.Feed

enum class FeedDecisionType {
    SNOOZE_30_DAYS,
    KEEP_FOREVER,
}

data class FeedDecisionRecord(
    val type: FeedDecisionType,
    val decidedAtMillis: Long,
)

/**
 * Aggregated per-feed reading statistics over an evaluation window.
 * The column aliases in ArticleDao.queryFeedEngagementStats must match these properties exactly.
 */
data class FeedEngagementStat(
    val feedId: String,
    val windowTotal: Int,
    val windowEngaged: Int,
    /** Oldest updateAt across ALL stored articles of this feed (not windowed), used to measure feed age. */
    val oldestUpdateAtMillis: Long?,
)

data class FeedAutopilotSuggestion(
    val feedId: String,
    val feedName: String,
    val feedIcon: String?,
    val windowTotal: Int,
    val windowEngaged: Int,
    val engagementRatio: Float,
    /** Integer percentage, rounded down (e.g. 4.76% -> 4) so a flagged feed never displays "5%". */
    val engagementPercent: Int,
    val windowDays: Int,
    val feedAgeDays: Int,
)

sealed interface FeedAutopilotEvaluation {
    data object Disabled : FeedAutopilotEvaluation
    data object NotLocalAccount : FeedAutopilotEvaluation
    data object RetentionTooShort : FeedAutopilotEvaluation
    data class Ready(
        val suggestions: List<FeedAutopilotSuggestion>,
        val windowDays: Int,
    ) : FeedAutopilotEvaluation
}
