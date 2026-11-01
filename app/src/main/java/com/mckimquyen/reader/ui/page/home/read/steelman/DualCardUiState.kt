package com.mckimquyen.reader.ui.page.home.read.steelman

import android.graphics.Bitmap
import androidx.annotation.StringRes

sealed interface DualCardUiState {
    data object Idle : DualCardUiState
    data object Rendering : DualCardUiState
    data class Ready(val bitmap: Bitmap, val wasCut: Boolean) : DualCardUiState
    data class Error(@StringRes val messageRes: Int) : DualCardUiState
}
