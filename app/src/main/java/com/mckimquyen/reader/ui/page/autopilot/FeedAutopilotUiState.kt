package com.mckimquyen.reader.ui.page.autopilot

import com.mckimquyen.reader.domain.autopilot.FeedAutopilotSuggestion

sealed interface FeedAutopilotUiState {
    data object Loading : FeedAutopilotUiState

    /** Analysis ran and nothing is rarely read. */
    data class Clean(val windowDays: Int) : FeedAutopilotUiState

    data class HasSuggestions(
        val suggestions: List<FeedAutopilotSuggestion>,
        val windowDays: Int,
    ) : FeedAutopilotUiState

    data object NotLocalAccount : FeedAutopilotUiState
    data object RetentionTooShort : FeedAutopilotUiState
    data object Disabled : FeedAutopilotUiState
    data object Error : FeedAutopilotUiState
}

/** The unsubscribe confirmation dialog; only shown after the starred count has been read, never auto-confirmed. */
data class PendingUnsubscribe(
    val feedId: String,
    val feedName: String,
    val starredCount: Int,
)
