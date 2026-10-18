package com.mckimquyen.reader.ui.page.statistics

import androidx.annotation.StringRes
import com.mckimquyen.reader.domain.model.statistics.ReadingStats

sealed interface ReadingStatisticsUiState {
    data object Loading : ReadingStatisticsUiState
    data object Empty : ReadingStatisticsUiState
    data class Content(val stats: ReadingStats) : ReadingStatisticsUiState
    data class Error(@StringRes val messageRes: Int) : ReadingStatisticsUiState
}
