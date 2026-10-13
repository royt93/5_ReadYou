package com.mckimquyen.reader.domain.model.rpg

/** Pure state machine for the article-body scroll + foreground dwell gate. Timestamps are monotonic millis. */
class ReadingCompletionGate(
    private val progressThreshold: Float = REQUIRED_BODY_PROGRESS,
    private val requiredForegroundMillis: Long = REQUIRED_FOREGROUND_MILLIS,
) {
    private var activeArticleId: String? = null
    private var foreground = false
    private var foregroundStartedAt: Long? = null
    private var accumulatedForegroundMillis = 0L
    private var emittedForCurrentVisit = false

    /** A different article visit starts a fresh continuous dwell period. */
    fun onArticleChanged(articleId: String, nowMillis: Long) {
        activeArticleId = articleId.takeIf(String::isNotBlank)
        accumulatedForegroundMillis = 0L
        foregroundStartedAt = nowMillis.takeIf { foreground && activeArticleId != null }
        emittedForCurrentVisit = false
    }

    /** Background time is excluded; returning to foreground continues the same article visit. */
    fun onForegroundChanged(isForeground: Boolean, nowMillis: Long) {
        if (foreground == isForeground) return
        if (foreground) {
            foregroundStartedAt?.let { accumulatedForegroundMillis += (nowMillis - it).coerceAtLeast(0L) }
            foregroundStartedAt = null
        } else if (activeArticleId != null) {
            foregroundStartedAt = nowMillis
        }
        foreground = isForeground
    }

    /**
     * Returns true exactly once per article visit after both body-index progress and foreground dwell qualify.
     * `bodyStartIndex` is the first LazyColumn item appended by Reader; metadata/quiz/ads are never in the range.
     */
    fun isComplete(
        articleId: String,
        lastVisibleBodyIndex: Int,
        bodyStartIndex: Int,
        bodyItemCount: Int,
        nowMillis: Long,
    ): Boolean {
        if (emittedForCurrentVisit || articleId.isBlank() || articleId != activeArticleId) return false
        if (bodyStartIndex < 0 || bodyItemCount <= 0 || lastVisibleBodyIndex < bodyStartIndex) return false
        val visibleBodyItems = (lastVisibleBodyIndex.toLong() - bodyStartIndex + 1L).coerceIn(0L, bodyItemCount.toLong())
        val progress = visibleBodyItems.toFloat() / bodyItemCount
        if (progress < progressThreshold) return false

        val currentForeground = if (foreground) {
            val startedAt = foregroundStartedAt ?: nowMillis.also { foregroundStartedAt = it }
            accumulatedForegroundMillis + (nowMillis - startedAt).coerceAtLeast(0L)
        } else {
            accumulatedForegroundMillis
        }
        if (currentForeground < requiredForegroundMillis) return false
        emittedForCurrentVisit = true
        return true
    }

    private companion object {
        const val REQUIRED_BODY_PROGRESS = 0.80f
        const val REQUIRED_FOREGROUND_MILLIS = 30_000L
    }
}
