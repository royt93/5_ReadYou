package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Position in the unit square; the canvas scales it to pixels. */
data class NodePos(val x: Float, val y: Float)

/**
 * Deterministic "sunflower" layout: heaviest concept in the middle, the rest spiral outward by
 * weight. O(n), no simulation, so identical graphs always render identically and 200 nodes is instant.
 */
object BrainGraphLayout {
    private const val GOLDEN_ANGLE = 2.399963229728653 // radians
    private const val MARGIN = 0.06f

    fun compute(graph: BrainGraph): Map<String, NodePos> {
        val ordered = graph.nodes.sortedWith(compareByDescending<BrainNode> { it.weight }.thenBy { it.id })
        if (ordered.isEmpty()) return emptyMap()
        if (ordered.size == 1) return mapOf(ordered.first().id to NodePos(0.5f, 0.5f))

        val radiusSpan = 0.5f - MARGIN
        val last = (ordered.size - 1).toFloat()
        return ordered.mapIndexed { index, node ->
            val r = radiusSpan * sqrt(index / last)
            val theta = index * GOLDEN_ANGLE
            node.id to NodePos(
                x = (0.5 + r * cos(theta)).toFloat().coerceIn(0f, 1f),
                y = (0.5 + r * sin(theta)).toFloat().coerceIn(0f, 1f),
            )
        }.toMap()
    }

    /** Nearest node whose centre is within [radius] of the tap, or null. All values are in unit-square space. */
    fun hitTest(positions: Map<String, NodePos>, x: Float, y: Float, radius: Float): String? =
        positions.entries
            .map { (id, p) -> id to ((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)) }
            .filter { it.second <= radius * radius }
            .minByOrNull { it.second }
            ?.first
}
