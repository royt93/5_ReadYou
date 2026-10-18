package com.mckimquyen.reader.ui.page.onthisday

import androidx.annotation.StringRes
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed

sealed interface OnThisDayUiState {
    data object Loading : OnThisDayUiState
    data object Empty : OnThisDayUiState
    data class Content(
        val articles: List<ArticleWithFeed>,
        val monthDay: String,
        val currentYear: Int,
    ) : OnThisDayUiState

    data class Error(@StringRes val messageRes: Int) : OnThisDayUiState
}
