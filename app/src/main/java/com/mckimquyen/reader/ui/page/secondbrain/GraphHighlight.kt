package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge

/** Which nodes and edges stay bright when a node is selected; everything else is dimmed by the canvas. */
object GraphHighlight {
    fun neighbours(selectedId: String?, edges: List<BrainEdge>): Set<String> {
        if (selectedId == null) return emptySet()
        val result = linkedSetOf<String>()
        edges.forEach { edge ->
            when (selectedId) {
                edge.from -> if (edge.to != selectedId) result += edge.to
                edge.to -> if (edge.from != selectedId) result += edge.from
            }
        }
        return result
    }

    fun isActiveEdge(selectedId: String?, edge: BrainEdge): Boolean =
        selectedId != null && (edge.from == selectedId || edge.to == selectedId)
}
