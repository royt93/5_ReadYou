package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph

sealed interface SecondBrainUiState {
    data object Loading : SecondBrainUiState
    data object Empty : SecondBrainUiState
    data object Error : SecondBrainUiState
    data class Ready(
        val graph: BrainGraph,
        val selectedNodeId: String?,
        /** Current slider window, always inside [graph.minTime]..[graph.maxTime] with from <= to. */
        val from: Long,
        val to: Long,
    ) : SecondBrainUiState
}

/** Pure state transitions so they can be unit tested without Android or coroutines. */
object SecondBrainStateReducer {
    fun fromGraph(graph: BrainGraph, selectedNodeId: String?, from: Long, to: Long): SecondBrainUiState {
        if (graph.nodes.isEmpty()) return SecondBrainUiState.Empty
        val lo = minOf(from, to).coerceIn(graph.minTime, graph.maxTime)
        val hi = maxOf(from, to).coerceIn(graph.minTime, graph.maxTime)
        val selection = selectedNodeId?.takeIf { id -> graph.nodes.any { it.id == id } }
        return SecondBrainUiState.Ready(graph, selection, lo, hi)
    }
}
