package com.mckimquyen.reader.ui.page.home.read.quote

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate

sealed interface QuoteCardUiState {
    data object Idle : QuoteCardUiState
    data object Rendering : QuoteCardUiState
    data class Ready(val bitmap: Bitmap, val template: QuoteCardTemplate, val wasCut: Boolean) : QuoteCardUiState
    data class Error(@StringRes val messageRes: Int) : QuoteCardUiState
}
