package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.ui.geometry.Offset
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphLabelsTest {
    private fun node(id: String, weight: Int) = BrainNode(id, id.uppercase(), weight, emptyList(), 0L, 0L)
    private val measure: (String) -> Pair<Float, Float> = { text -> text.length * 10f to 12f }

    private fun select(
        nodes: List<BrainNode>,
        positions: Map<String, Offset>,
        selected: String? = null,
        neighbours: Set<String> = emptySet(),
        scale: Float = 1f,
        width: Float = 1000f,
        height: Float = 800f,
    ) = GraphLabels.select(nodes, positions, selected, neighbours, scale, width, height, measure)

    private fun spread(n: Int): Pair<List<BrainNode>, Map<String, Offset>> {
        val nodes = (1..n).map { node("n$it", 100 - it) }
        val positions = nodes.mapIndexed { i, nd -> nd.id to Offset(40f + (i % 10) * 90f, 40f + (i / 10) * 60f) }.toMap()
        return nodes to positions
    }

    @Test
    fun budget_growsLinearlyFromBaseToZoomedMax() {
        assertEquals(GraphLabels.MAX_BASE_LABELS, GraphLabels.budget(GraphViewport.MIN_SCALE))
        assertEquals(GraphLabels.MAX_ZOOMED_LABELS, GraphLabels.budget(GraphViewport.MAX_SCALE))
        assertTrue(GraphLabels.budget(3f) in GraphLabels.MAX_BASE_LABELS..GraphLabels.MAX_ZOOMED_LABELS)
    }

    @Test
    fun atScaleOne_noMoreThanTheBaseBudgetOfOrdinaryLabels() {
        val (nodes, positions) = spread(40)
        val labels = select(nodes, positions)
        assertEquals(GraphLabels.MAX_BASE_LABELS, labels.size)
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n6", "n7", "n8"), labels.map { it.nodeId })
    }

    @Test
    fun zoomingIn_showsMoreLabels() {
        val (nodes, positions) = spread(60)
        val few = select(nodes, positions, scale = 1f).size
        val many = select(nodes, positions, scale = GraphViewport.MAX_SCALE).size
        assertTrue(many > few)
        assertTrue(many <= GraphLabels.MAX_ZOOMED_LABELS)
    }

    @Test
    fun selectedAndNeighbours_areAlwaysPlacedFirst_andDoNotConsumeTheBudget() {
        val (nodes, positions) = spread(40)
        val labels = select(nodes, positions, selected = "n40", neighbours = setOf("n39", "n38"))
        val ids = labels.map { it.nodeId }
        // weights are 100 - index, so n38 (62) is heavier than n39 (61) and is placed before it
        assertEquals(listOf("n40", "n38", "n39"), ids.take(3))
        assertEquals(3 + GraphLabels.MAX_BASE_LABELS, labels.size)
    }

    @Test
    fun overlappingLabel_isDropped_butTheSelectedNodeIsKept() {
        val nodes = listOf(node("aa", 5), node("bb", 9))
        val positions = mapOf("aa" to Offset(500f, 400f), "bb" to Offset(505f, 402f))
        val labels = select(nodes, positions, selected = "aa")
        assertEquals(listOf("aa"), labels.map { it.nodeId })
    }

    @Test
    fun noTwoLabelsOverlap() {
        val (nodes, positions) = spread(60)
        val labels = select(nodes, positions, scale = GraphViewport.MAX_SCALE)
        for (i in labels.indices) for (j in i + 1 until labels.size) {
            val a = labels[i]
            val b = labels[j]
            val overlap = a.left < b.left + b.width && b.left < a.left + a.width &&
                a.top < b.top + b.height && b.top < a.top + a.height
            assertFalse("${a.nodeId} overlaps ${b.nodeId}", overlap)
        }
    }

    @Test
    fun nodeOutsideTheCanvas_getsNoLabel() {
        val nodes = listOf(node("in", 1), node("out", 9))
        val positions = mapOf("in" to Offset(100f, 100f), "out" to Offset(-50f, 100f))
        assertEquals(listOf("in"), select(nodes, positions).map { it.nodeId })
    }

    @Test
    fun label_isClampedInsideTheCanvas() {
        val nodes = listOf(node("edgenode", 1))
        val positions = mapOf("edgenode" to Offset(995f, 5f))
        val box = select(nodes, positions).single()
        assertTrue(box.left >= 0f && box.left + box.width <= 1000f)
        assertTrue(box.top >= 0f && box.top + box.height <= 800f)
    }

    @Test
    fun selection_isDeterministic_forEqualWeights() {
        val nodes = listOf(node("b", 1), node("a", 1), node("c", 1))
        val positions = mapOf("a" to Offset(100f, 100f), "b" to Offset(300f, 100f), "c" to Offset(500f, 100f))
        assertEquals(select(nodes, positions).map { it.nodeId }, select(nodes.reversed(), positions).map { it.nodeId })
        assertEquals(listOf("a", "b", "c"), select(nodes, positions).map { it.nodeId })
    }

    @Test
    fun unknownPositionOrEmptyInput_isSafe() {
        assertTrue(select(emptyList(), emptyMap()).isEmpty())
        assertTrue(select(listOf(node("x", 1)), emptyMap()).isEmpty())
    }
}
