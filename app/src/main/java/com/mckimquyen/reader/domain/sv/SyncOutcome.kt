package com.mckimquyen.reader.domain.sv

/** Whether a sync reached any feed. Zero feeds to sync is not a failure; tried feeds that all failed is. */
object SyncOutcome {
    fun allFailed(feedsTried: Int, feedsFetched: Int): Boolean = feedsTried > 0 && feedsFetched == 0
}
