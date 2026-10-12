package com.mckimquyen.reader.ui.page.secondbrain

import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondBrainStateReducerTest {
    private fun node(id: String) = BrainNode(id, id, 1, listOf("a-$id"), 10L, 20L)
    private val graph = BrainGraph(listOf(node("ai"), node("chips")), listOf(BrainEdge("ai", "chips", 1)), 10L, 100L)

    @Test
    fun fromGraph_emptyGraph_isEmptyState() {
        assertEquals(SecondBrainUiState.Empty, SecondBrainStateReducer.fromGraph(BrainGraph.EMPTY, null, 0L, 0L))
    }

    @Test
    fun fromGraph_populated_startsWithFullRangeAndNoSelection() {
        val state = SecondBrainStateReducer.fromGraph(graph, null, graph.minTime, graph.maxTime)
            as SecondBrainUiState.Ready

        assertNull(state.selectedNodeId)
        assertEquals(graph.minTime, state.from)
        assertEquals(graph.maxTime, state.to)
    }

    @Test
    fun fromGraph_keepsSelection_whenNodeStillInGraph() {
        val state = SecondBrainStateReducer.fromGraph(graph, "ai", 10L, 100L) as SecondBrainUiState.Ready
        assertEquals("ai", state.selectedNodeId)
    }

    @Test
    fun fromGraph_dropsSelection_whenNodeFilteredOut() {
        val state = SecondBrainStateReducer.fromGraph(graph, "gone", 10L, 100L) as SecondBrainUiState.Ready
        assertNull(state.selectedNodeId)
    }

    @Test
    fun fromGraph_clampsRangeIntoGraphBounds_andKeepsFromBeforeTo() {
        val state = SecondBrainStateReducer.fromGraph(graph, null, 500L, 5L) as SecondBrainUiState.Ready
        assertTrue(state.from in graph.minTime..graph.maxTime)
        assertTrue(state.to in graph.minTime..graph.maxTime)
        assertTrue(state.from <= state.to)
    }

    @Test
    fun fromGraph_emptyWindowOverNonEmptyData_staysReady_soTheSliderCanWidenAgain() {
        // The builder keeps minTime/maxTime of ALL articles even when the chosen window has no concept.
        val quietWindow = BrainGraph(emptyList(), emptyList(), 10L, 100L)

        val state = SecondBrainStateReducer.fromGraph(quietWindow, null, 40L, 50L)

        assertTrue("an empty window must not strand the user without a slider", state is SecondBrainUiState.Ready)
        state as SecondBrainUiState.Ready
        assertEquals(40L to 50L, state.from to state.to)
        assertTrue(state.graph.nodes.isEmpty())
    }

    @Test
    fun fromGraph_noDataAtAll_isStillEmpty() {
        assertEquals(SecondBrainUiState.Empty, SecondBrainStateReducer.fromGraph(BrainGraph.EMPTY, null, 0L, 0L))
        // A single instant of data has no range to widen, so there is nothing to keep the slider for.
        val oneInstant = BrainGraph(emptyList(), emptyList(), 5L, 5L)
        assertEquals(SecondBrainUiState.Empty, SecondBrainStateReducer.fromGraph(oneInstant, null, 5L, 5L))
    }
}
