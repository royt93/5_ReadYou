package com.mckimquyen.reader.ui.page.home.read.deepdive

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.sv.AiDeepDiveResult
import com.mckimquyen.reader.domain.sv.AiDeepDiveService
import com.mckimquyen.reader.infrastructure.ai.DeepDivePdfRenderer
import com.mckimquyen.reader.infrastructure.ai.DeepDiveSharer
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.deepDiveGeminiConsent
import com.mckimquyen.reader.ui.ext.setDeepDiveGeminiConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * Drives the Deep Dive sheet (FRONT-01). The single place that decides whether Gemini is called: only when the stored
 * consent is exactly `true`, and never twice for something whose report is cached. It is handed three strings (id,
 * title, body HTML) and never a whole record, a link, a feed or an account.
 */
@HiltViewModel
class DeepDiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: AiDeepDiveService,
    private val cache: DeepDiveCache,
    private val sharer: DeepDiveSharer,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<DeepDiveUiState>(DeepDiveUiState.Idle)
    val state: StateFlow<DeepDiveUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var pending: Pending? = null

    private data class Pending(val articleId: String, val title: String, val bodyHtml: String)

    fun open(articleId: String, title: String, bodyHtml: String) {
        if (pending?.articleId == articleId && _state.value is DeepDiveUiState.Loading) return
        job?.cancel()
        val request = Pending(articleId, title, bodyHtml)
        pending = request
        cache.get(articleId)?.let {
            _state.value = DeepDiveUiState.Ready(it)
            return
        }
        // Loading first, so a report of the previous article is never shown for this one while consent is read.
        _state.value = DeepDiveUiState.Loading
        job = viewModelScope.launch {
            when (withContext(ioDispatcher) { context.deepDiveGeminiConsent }) {
                null -> _state.value = DeepDiveUiState.AwaitingConsent
                false -> _state.value = DeepDiveUiState.ConsentDeclined
                true -> generate(request)
            }
        }
    }

    /** The user answered the in-sheet question. Agreeing continues with the article that is waiting. */
    fun onConsent(agreed: Boolean) {
        val waiting = pending
        job?.cancel()
        job = viewModelScope.launch {
            withContext(ioDispatcher) { context.setDeepDiveGeminiConsent(agreed) }
            if (!agreed) {
                _state.value = DeepDiveUiState.ConsentDeclined
            } else if (waiting != null) {
                generate(waiting)
            }
        }
    }

    /** "Not now": nothing is stored and the sheet closes, so the question is asked again next time. */
    fun onConsentLater() = dismiss()

    /** Asks again after a failure. Only the user's tap gets here; nothing retries by itself. */
    fun retry() {
        val waiting = pending ?: return
        if (_state.value !is DeepDiveUiState.Failed) return
        job?.cancel()
        job = viewModelScope.launch { generate(waiting) }
    }

    fun dismiss() {
        job?.cancel()
        job = null
        pending = null
        _state.value = DeepDiveUiState.Idle
    }

    private suspend fun generate(request: Pending) {
        _state.value = DeepDiveUiState.Loading
        val result = withContext(ioDispatcher) {
            service.generate(request.articleId, request.title, request.bodyHtml, Locale.getDefault().toLanguageTag())
        }
        // A newer article or dismiss cancels [job] first, so withContext above throws CancellationException
        // instead of returning an old answer here.
        _state.value = when (result) {
            is AiDeepDiveResult.Ready -> {
                cache.put(request.articleId, result.report)
                DeepDiveUiState.Ready(result.report)
            }
            is AiDeepDiveResult.Failed -> DeepDiveUiState.Failed(result.reason)
        }
    }

    /** Builds the one-page PDF for the report on screen and returns a chooser, or null (no report or I/O failure). */
    suspend fun prepareShare(texts: DeepDivePdfTexts, chooserTitle: String): Intent? {
        val ready = _state.value as? DeepDiveUiState.Ready ?: return null
        val title = pending?.title.orEmpty()
        return try {
            withContext(ioDispatcher) {
                val plan = DeepDivePdfLayout.plan(ready.report, title, texts, DeepDivePdfRenderer.measurer)
                val bytes = DeepDivePdfRenderer.render(plan, title, texts)
                sharer.buildShareIntent(sharer.write(bytes, title), chooserTitle)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only the exception type: a message could carry the title.
            Log.w(TAG, "Deep Dive PDF failed: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "DeepDiveViewModel"
    }
}
