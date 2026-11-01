package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphHighlightTest {
    private val edges = listOf(
        BrainEdge("ai", "chips", 2),
        BrainEdge("ai", "energy", 1),
        BrainEdge("chips", "energy", 1),
        BrainEdge("solo", "solo", 1),
    )

    @Test
    fun neighbours_returnsDirectNeighboursInEitherDirection() {
        assertEquals(setOf("chips", "energy"), GraphHighlight.neighbours("ai", edges))
        assertEquals(setOf("ai", "energy"), GraphHighlight.neighbours("chips", edges))
    }

    @Test
    fun neighbours_isEmptyWithoutSelection_orForUnknownNode() {
        assertTrue(GraphHighlight.neighbours(null, edges).isEmpty())
        assertTrue(GraphHighlight.neighbours("ghost", edges).isEmpty())
    }

    @Test
    fun neighbours_ignoresSelfLoops_andNeverIncludesTheNodeItself() {
        assertTrue(GraphHighlight.neighbours("solo", edges).isEmpty())
        assertFalse(GraphHighlight.neighbours("ai", edges).contains("ai"))
    }

    @Test
    fun isActiveEdge_isTrueOnlyForEdgesTouchingTheSelection() {
        assertTrue(GraphHighlight.isActiveEdge("ai", edges[0]))
        assertTrue(GraphHighlight.isActiveEdge("energy", edges[1]))
        assertFalse(GraphHighlight.isActiveEdge("ai", edges[2]))
        assertFalse(GraphHighlight.isActiveEdge(null, edges[0]))
    }
}
