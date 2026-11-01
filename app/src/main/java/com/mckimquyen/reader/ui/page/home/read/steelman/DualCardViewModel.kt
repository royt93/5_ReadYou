package com.mckimquyen.reader.ui.page.home.read.steelman

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.domain.steelman.DualPerspectiveTruncator
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.quote.DualBitmapRenderer
import com.mckimquyen.reader.infrastructure.quote.QuoteCardSharer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DualCardViewModel @Inject constructor(
    private val sharer: QuoteCardSharer,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val renderer: DualBitmapRenderer,
) : ViewModel() {

    private val _state = MutableStateFlow<DualCardUiState>(DualCardUiState.Idle)
    val state: StateFlow<DualCardUiState> = _state.asStateFlow()

    private var renderJob: Job? = null
    private var lastReady: DualCardUiState.Ready? = null

    fun start(spec: DualPerspectiveSpec) {
        renderJob?.cancel()
        _state.value = DualCardUiState.Rendering
        renderJob = viewModelScope.launch {
            try {
                val bitmap = withContext(defaultDispatcher) { renderer.render(spec) }
                val wasCut = DualPerspectiveTruncator.truncate(spec.topTitle).wasCut ||
                    DualPerspectiveTruncator.truncate(spec.bottomTitle).wasCut
                publish(DualCardUiState.Ready(bitmap, wasCut))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "render two-headline card failed", e)
                publish(DualCardUiState.Error(R.string.echo_dual_error))
            }
        }
    }

    /** Writes the PNG and builds the chooser; null when not ready or the write failed (state becomes Error). */
    suspend fun prepareShareIntent(chooserTitle: String, shareText: String): Intent? {
        val ready = _state.value as? DualCardUiState.Ready ?: return null
        return try {
            val file = sharer.writePng(ready.bitmap, FILE_PREFIX)
            sharer.buildShareIntent(file, chooserTitle, shareText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "share two-headline card failed", e)
            _state.value = DualCardUiState.Error(R.string.echo_dual_error)
            null
        }
    }

    fun dismiss() {
        renderJob?.cancel()
        publish(DualCardUiState.Idle)
    }

    /** Sets the new state first, then recycles the previous bitmap so the UI never draws a recycled one. */
    private fun publish(next: DualCardUiState) {
        val previous = lastReady
        _state.value = next
        lastReady = next as? DualCardUiState.Ready
        if (previous != null && previous.bitmap !== lastReady?.bitmap) previous.bitmap.recycle()
    }

    override fun onCleared() {
        renderJob?.cancel()
        lastReady?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        lastReady = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "DualCardViewModel"
        const val FILE_PREFIX = "dual"
    }
}
