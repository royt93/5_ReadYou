package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraphBuilder
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainGraphLayoutTest {
    private fun node(id: String, weight: Int = 1) = BrainNode(id, id, weight, emptyList(), 0L, 0L)
    private fun graph(n: Int) = BrainGraph(
        (1..n).map { node("n$it", it) },
        (2..n).map { BrainEdge("n${it - 1}", "n$it", 1) },
        0L, 1L,
    )

    @Test
    fun layout_emptyGraph_hasNoPositions() {
        assertTrue(BrainGraphLayout.compute(BrainGraph.EMPTY).isEmpty())
    }

    @Test
    fun layout_singleNode_isCentered() {
        val p = BrainGraphLayout.compute(graph(1)).getValue("n1")
        assertEquals(0.5f, p.x, 0.0001f)
        assertEquals(0.5f, p.y, 0.0001f)
    }

    @Test
    fun layout_isDeterministic_forTheSameGraph() {
        assertEquals(BrainGraphLayout.compute(graph(30)), BrainGraphLayout.compute(graph(30)))
    }

    @Test
    fun layout_keepsEveryNodeInsideTheUnitSquare_andGivesDistinctPositions() {
        val positions = BrainGraphLayout.compute(graph(BrainGraphBuilder.MAX_NODES))

        assertEquals(BrainGraphBuilder.MAX_NODES, positions.size)
        assertTrue(positions.values.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(positions.size, positions.values.map { it.x to it.y }.toSet().size)
    }

    @Test
    fun layout_200Nodes_finishesWellUnderOneSecond() {
        val g = graph(BrainGraphBuilder.MAX_NODES)
        BrainGraphLayout.compute(g) // warm up
        val start = System.nanoTime()
        BrainGraphLayout.compute(g)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("layout took ${ms}ms", ms < 1_000)
    }

    @Test
    fun hitTest_returnsNearestNodeWithinRadius_elseNull() {
        val positions = mapOf("a" to NodePos(0.2f, 0.2f), "b" to NodePos(0.8f, 0.8f))

        assertEquals("a", BrainGraphLayout.hitTest(positions, 0.21f, 0.19f, radius = 0.05f))
        assertEquals(null, BrainGraphLayout.hitTest(positions, 0.5f, 0.5f, radius = 0.05f))
    }
}
