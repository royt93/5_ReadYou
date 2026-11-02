package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyGraphBuilderTest {

    private fun star(id: String, date: Long, vararg concepts: String) =
        GalaxyStar(id, "Title $id", "Feed", date, concepts.toSet())

    @Test
    fun build_connectsStarsBySharedConceptAndSortsStrongestFirst() {
        val graph = GalaxyGraphBuilder.build(
            listOf(star("a", 1, "ai", "chips"), star("b", 2, "ai", "chips"), star("c", 3, "ai")),
        )
        assertEquals(GalaxyLine("a", "b", 2), graph.lines.first())
        assertEquals(3, graph.stars.size)
    }

    @Test
    fun build_filtersInclusiveWindow() {
        val graph = GalaxyGraphBuilder.build(
            listOf(star("old", 1, "x"), star("in", 2, "x"), star("new", 3, "x")), from = 2, to = 3,
        )
        assertEquals(listOf("in", "new"), graph.stars.map { it.id }.sorted())
    }

    @Test
    fun build_normalizesConceptCaseAndWhitespace() {
        val graph = GalaxyGraphBuilder.build(listOf(star("a", 1, " AI "), star("b", 2, "ai")))
        assertEquals(listOf(GalaxyLine("a", "b", 1)), graph.lines)
    }

    @Test
    fun build_starsWithoutSharedConceptHaveNoLine() {
        val graph = GalaxyGraphBuilder.build(listOf(star("a", 1, "ai"), star("b", 2, "sport"), star("c", 3)))
        assertEquals(3, graph.stars.size)
        assertTrue(graph.lines.isEmpty())
    }

    @Test
    fun build_emptyAndSingleInput() {
        assertEquals(GalaxyGraph.EMPTY, GalaxyGraphBuilder.build(emptyList()))
        val single = GalaxyGraphBuilder.build(listOf(star("a", 1, "ai")))
        assertEquals(1, single.stars.size)
        assertTrue(single.lines.isEmpty())
    }

    @Test
    fun build_keepsNewestStarsWhenOverTheCap() {
        val stars = (0..GalaxyGraphBuilder.MAX_STARS).map { star("%04d".format(it), it.toLong(), "common") }
        val graph = GalaxyGraphBuilder.build(stars)
        assertEquals(GalaxyGraphBuilder.MAX_STARS, graph.stars.size)
        assertTrue(graph.stars.none { it.id == "0000" })
        assertTrue(graph.stars.any { it.id == "%04d".format(GalaxyGraphBuilder.MAX_STARS) })
    }

    @Test
    fun build_capsLinesAndNeighboursForAVeryCommonConcept() {
        val stars = (0 until GalaxyGraphBuilder.MAX_STARS).map { star("%04d".format(it), it.toLong(), "common") }
        val graph = GalaxyGraphBuilder.build(stars)
        assertEquals(GalaxyGraphBuilder.MAX_LINES, graph.lines.size)
        val degree = graph.lines.flatMap { listOf(it.a, it.b) }.groupingBy { it }.eachCount()
        assertTrue(degree.values.all { it <= GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR * 2 })
    }

    @Test
    fun build_linesReferenceExistingStarsAndNeverSelfLink() {
        val graph = GalaxyGraphBuilder.build((0 until 50).map { star("s$it", it.toLong(), "a", "b") })
        val ids = graph.stars.map { it.id }.toSet()
        assertTrue(graph.lines.all { it.a in ids && it.b in ids && it.a != it.b })
        assertEquals(graph.lines.size, graph.lines.map { it.a to it.b }.toSet().size)
    }

    @Test
    fun build_isDeterministicRegardlessOfInputOrder() {
        val input = (0 until 40).map { star("s$it", (it % 7).toLong(), "a", if (it % 2 == 0) "b" else "c") }
        assertEquals(GalaxyGraphBuilder.build(input), GalaxyGraphBuilder.build(input.reversed()))
        assertNotEquals(GalaxyGraph.EMPTY, GalaxyGraphBuilder.build(input))
    }
}
