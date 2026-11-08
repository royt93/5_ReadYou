package com.mckimquyen.reader.ui.page.archivequery

import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryResult

/** Why a submitted question or date range was not searched; the page maps each to a localized message. */
enum class ArchiveQueryProblem {
    Blank,
    TooLong,
    BlankTopic,
    /** The question mentions a time bound we cannot read; the user must pick dates explicitly. */
    NeedsDateClarification,
    /** Only one of the start/end dates was provided. */
    IncompleteRange,
    RangeReversed,
    RangeInFuture,
    RangeTooWide,
}

sealed interface ArchiveQueryUiState {
    data object Idle : ArchiveQueryUiState

    data class InvalidInput(val problem: ArchiveQueryProblem) : ArchiveQueryUiState

    data class Searching(val topic: String, val range: ArchiveQueryRange) : ArchiveQueryUiState

    /** Nothing matched in the whole range; [range] is shown so the user knows what was searched. */
    data class NoResults(val topic: String, val range: ArchiveQueryRange) : ArchiveQueryUiState

    data class Ready(val topic: String, val result: ArchiveQueryResult) : ArchiveQueryUiState

    data object Error : ArchiveQueryUiState
}
