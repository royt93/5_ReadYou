package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.ui.geometry.Offset
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode

data class LabelBox(
    val nodeId: String,
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Chooses which node labels to draw. Selected node first, then its neighbours (neither counts against the
 * budget), then the heaviest remaining nodes up to a zoom-dependent budget. A label that would overlap an
 * already placed one is skipped, so text never piles up.
 */
object GraphLabels {
    const val MAX_BASE_LABELS = 8
    const val MAX_ZOOMED_LABELS = 24
    private const val GAP = 6f

    fun budget(scale: Float): Int {
        val span = GraphViewport.MAX_SCALE - GraphViewport.MIN_SCALE
        val t = ((scale - GraphViewport.MIN_SCALE) / span).coerceIn(0f, 1f)
        return (MAX_BASE_LABELS + (MAX_ZOOMED_LABELS - MAX_BASE_LABELS) * t).toInt()
    }

    fun select(
        nodes: List<BrainNode>,
        screenPositions: Map<String, Offset>,
        selectedId: String?,
        neighbourIds: Set<String>,
        scale: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        measure: (String) -> Pair<Float, Float>,
    ): List<LabelBox> {
        val byId = nodes.associateBy { it.id }
        val ordinary = nodes
            .filter { it.id != selectedId && it.id !in neighbourIds }
            .sortedWith(compareByDescending<BrainNode> { it.weight }.thenBy { it.id })
        val priority = buildList {
            selectedId?.let { byId[it] }?.let(::add)
            neighbourIds.mapNotNull { byId[it] }
                .sortedWith(compareByDescending<BrainNode> { it.weight }.thenBy { it.id })
                .forEach(::add)
        }

        val placed = mutableListOf<LabelBox>()
        var ordinaryPlaced = 0
        val limit = budget(scale)

        fun tryPlace(node: BrainNode): Boolean {
            val center = screenPositions[node.id] ?: return false
            if (center.x < 0f || center.y < 0f || center.x > canvasWidth || center.y > canvasHeight) return false
            val (w, h) = measure(node.label)
            val left = (center.x - w / 2f).coerceIn(0f, (canvasWidth - w).coerceAtLeast(0f))
            val above = center.y - GAP - h
            val top = (if (above >= 0f) above else center.y + GAP).coerceIn(0f, (canvasHeight - h).coerceAtLeast(0f))
            val box = LabelBox(node.id, node.label, left, top, w, h)
            if (placed.any { it.overlaps(box) }) return false
            placed += box
            return true
        }

        priority.forEach { tryPlace(it) }
        for (node in ordinary) {
            if (ordinaryPlaced >= limit) break
            if (tryPlace(node)) ordinaryPlaced++
        }
        return placed
    }

    private fun LabelBox.overlaps(o: LabelBox): Boolean =
        left < o.left + o.width && o.left < left + width && top < o.top + o.height && o.top < top + height
}
