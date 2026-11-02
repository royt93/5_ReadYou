package com.mckimquyen.reader.ui.page.secondbrain.galaxy

import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraph

sealed interface GalaxyUiState {
    data object Loading : GalaxyUiState

    /** No read article in the chosen window (or none read at all). */
    data object Empty : GalaxyUiState
    data object Error : GalaxyUiState
    data class Ready(
        val graph: GalaxyGraph,
        val selectedStarId: String?,
        /** Current slider window, always inside [minTime]..[maxTime] with from <= to. */
        val from: Long,
        val to: Long,
        /** Date span of ALL read articles, ignoring the window, so the slider can always be widened again. */
        val minTime: Long,
        val maxTime: Long,
    ) : GalaxyUiState
}
