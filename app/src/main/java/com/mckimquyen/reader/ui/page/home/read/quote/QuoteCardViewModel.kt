package com.mckimquyen.reader.ui.page.home.read.quote

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.quote.QuoteCardSpec
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import com.mckimquyen.reader.domain.model.quote.QuoteTextTruncator
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.quote.QuoteBitmapRenderer
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
class QuoteCardViewModel @Inject constructor(
    private val sharer: QuoteCardSharer,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val renderer: QuoteBitmapRenderer,
) : ViewModel() {

    private val _state = MutableStateFlow<QuoteCardUiState>(QuoteCardUiState.Idle)
    val state: StateFlow<QuoteCardUiState> = _state.asStateFlow()

    private val _selectedTemplate = MutableStateFlow(DEFAULT_TEMPLATE)
    val selectedTemplate: StateFlow<QuoteCardTemplate> = _selectedTemplate.asStateFlow()

    private var spec: QuoteCardSpec? = null
    private var renderJob: Job? = null
    private var lastReady: QuoteCardUiState.Ready? = null

    /** Bắt đầu một phiên mới với template mặc định. */
    fun start(newSpec: QuoteCardSpec) {
        spec = newSpec
        _selectedTemplate.value = DEFAULT_TEMPLATE
        render(newSpec, DEFAULT_TEMPLATE)
    }

    fun selectTemplate(template: QuoteCardTemplate) {
        val current = spec ?: return
        _selectedTemplate.value = template
        render(current, template)
    }

    /** Ghi PNG và dựng intent chia sẻ; null khi chưa sẵn sàng hoặc ghi lỗi (state chuyển Error). */
    suspend fun prepareShareIntent(chooserTitle: String): Intent? {
        val ready = _state.value as? QuoteCardUiState.Ready ?: return null
        return try {
            val file = sharer.writePng(ready.bitmap, ready.template)
            sharer.buildShareIntent(file, chooserTitle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "share quote card failed", e)
            _state.value = QuoteCardUiState.Error(R.string.quote_card_error)
            null
        }
    }

    fun dismiss() {
        renderJob?.cancel()
        spec = null
        publish(QuoteCardUiState.Idle)
    }

    private fun render(forSpec: QuoteCardSpec, template: QuoteCardTemplate) {
        renderJob?.cancel()
        // Bitmap cũ chỉ bị recycle trong publish() sau khi state mới đã thay thế, nên UI không vẽ bitmap đã recycle.
        _state.value = QuoteCardUiState.Rendering
        renderJob = viewModelScope.launch {
            try {
                val bitmap = withContext(defaultDispatcher) { renderer.render(forSpec, template) }
                val wasCut = QuoteTextTruncator.truncate(forSpec.quote).wasCut
                publish(QuoteCardUiState.Ready(bitmap, template, wasCut))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "render quote card failed", e)
                publish(QuoteCardUiState.Error(R.string.quote_card_error))
            }
        }
    }

    /** Đặt state mới rồi recycle bitmap của state Ready trước đó (sau khi đã thay để UI không còn tham chiếu). */
    private fun publish(next: QuoteCardUiState) {
        val previous = lastReady
        _state.value = next
        lastReady = (next as? QuoteCardUiState.Ready)
        if (previous != null && previous.bitmap !== lastReady?.bitmap) previous.bitmap.recycle()
    }

    override fun onCleared() {
        renderJob?.cancel()
        lastReady?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        lastReady = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "QuoteCardViewModel"
        val DEFAULT_TEMPLATE = QuoteCardTemplate.MINIMALIST
    }
}
