package com.mckimquyen.reader.ui.page.secondbrain

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.sv.SecondBrainSv
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecondBrainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secondBrainSv: SecondBrainSv,
) : ViewModel() {
    private val _state = MutableStateFlow<SecondBrainUiState>(SecondBrainUiState.Loading)
    val state: StateFlow<SecondBrainUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        reload(from = Long.MIN_VALUE, to = Long.MAX_VALUE, selected = null)
    }

    fun selectNode(id: String?) {
        val current = _state.value as? SecondBrainUiState.Ready ?: return
        _state.value = current.copy(selectedNodeId = id?.takeIf { current.graph.nodes.any { n -> n.id == it } })
    }

    /** Re-filters the graph to the slider window; the slider's own bounds stay those of the full data. */
    fun setRange(from: Long, to: Long) {
        val current = _state.value as? SecondBrainUiState.Ready ?: return
        reload(from, to, current.selectedNodeId, current.graph.minTime, current.graph.maxTime)
    }

    private fun reload(from: Long, to: Long, selected: String?, minBound: Long? = null, maxBound: Long? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_state.value !is SecondBrainUiState.Ready) _state.value = SecondBrainUiState.Loading
            try {
                val graph = secondBrainSv.load(context.currentAccountId, from, to)
                val stableMin = minBound ?: graph.minTime
                val stableMax = maxBound ?: graph.maxTime
                val bounded = graph.copy(minTime = stableMin, maxTime = stableMax)
                _state.value = SecondBrainStateReducer.fromGraph(
                    bounded, selected,
                    from.coerceAtLeast(stableMin), to.coerceAtMost(stableMax),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Second Brain load failed: ${e.javaClass.simpleName}", e)
                _state.value = SecondBrainUiState.Error
            }
        }
    }

    private companion object {
        const val TAG = "SecondBrain"
    }
}
