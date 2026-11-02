package com.mckimquyen.reader.ui.page.secondbrain.galaxy

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.sv.GalaxySv
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.currentAccountId
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
import javax.inject.Inject

@HiltViewModel
class GalaxyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val galaxySv: GalaxySv,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow<GalaxyUiState>(GalaxyUiState.Loading)
    val state: StateFlow<GalaxyUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var from = Long.MIN_VALUE
    private var to = Long.MAX_VALUE

    init {
        reload()
    }

    fun refresh() {
        from = Long.MIN_VALUE
        to = Long.MAX_VALUE
        reload()
    }

    /** Re-filters the galaxy to the shared time-slider window. A newer window cancels any load still running. */
    fun setRange(from: Long, to: Long) {
        this.from = minOf(from, to)
        this.to = maxOf(from, to)
        reload()
    }

    fun selectStar(id: String?) {
        val current = _state.value as? GalaxyUiState.Ready ?: return
        _state.value = current.copy(selectedStarId = id?.takeIf { known -> current.graph.stars.any { it.id == known } })
    }

    private fun reload() {
        loadJob?.cancel()
        val windowFrom = from
        val windowTo = to
        val selected = (_state.value as? GalaxyUiState.Ready)?.selectedStarId
        loadJob = viewModelScope.launch {
            if (_state.value !is GalaxyUiState.Ready) _state.value = GalaxyUiState.Loading
            try {
                // currentAccountId reads DataStore synchronously, so keep it off the main thread.
                val accountId = withContext(ioDispatcher) { context.currentAccountId }
                val graph = galaxySv.load(accountId, windowFrom, windowTo)
                _state.value = if (graph.stars.isEmpty()) {
                    GalaxyUiState.Empty
                } else {
                    GalaxyUiState.Ready(
                        graph = graph,
                        selectedStarId = selected?.takeIf { id -> graph.stars.any { it.id == id } },
                        from = windowFrom,
                        to = windowTo,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Galaxy load failed: ${e.javaClass.simpleName}", e)
                _state.value = GalaxyUiState.Error
            }
        }
    }

    private companion object {
        const val TAG = "Galaxy"
    }
}
