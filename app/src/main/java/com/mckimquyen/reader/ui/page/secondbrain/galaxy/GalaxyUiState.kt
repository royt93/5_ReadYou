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
        val from: Long,
        val to: Long,
    ) : GalaxyUiState
}
