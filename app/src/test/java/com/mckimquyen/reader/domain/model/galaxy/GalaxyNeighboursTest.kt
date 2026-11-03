package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyNeighboursTest {
    private fun star(id: String) = GalaxyStar(id, "Title $id", "BBC", 1L, emptySet())
    private fun title(a: String, b: String, vararg terms: String) =
        GalaxyLine(a, b, 0, GalaxyLineKind.TITLE_WORDS, terms.toList())

    private val stars = listOf("a", "b", "c", "d", "e").map(::star)

    @Test fun returnsTheOtherEndOfEachLineWithItsSharedWords() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "trump", "putin"), title("c", "a", "burnham", "city")))
        val result = GalaxyNeighbours.of(graph, "a")
        assertEquals(listOf("b", "c"), result.map { it.star.id })
        assertEquals(listOf("trump", "putin"), result[0].sharedTerms)
        assertEquals(listOf("burnham", "city"), result[1].sharedTerms)
        assertEquals(GalaxyLineKind.TITLE_WORDS, result[0].kind)
    }

    @Test fun worksWhicheverEndTheStarIsOn() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "x1", "x2")))
        assertEquals(listOf("b"), GalaxyNeighbours.of(graph, "a").map { it.star.id })
        assertEquals(listOf("a"), GalaxyNeighbours.of(graph, "b").map { it.star.id })
    }

    @Test fun keepsTheStrengthOrderOfTheGraphAndCutsAtTheLimit() {
        val graph = GalaxyGraph(
            stars,
            listOf(title("a", "b", "t1", "t2"), title("a", "c", "t3", "t4"), title("a", "d", "t5", "t6"), title("a", "e", "t7", "t8")),
        )
        assertEquals(listOf("b", "c", "d"), GalaxyNeighbours.of(graph, "a").map { it.star.id })
        assertEquals(listOf("b"), GalaxyNeighbours.of(graph, "a", limit = 1).map { it.star.id })
        assertEquals(4, GalaxyNeighbours.of(graph, "a", limit = 10).size)
    }

    @Test fun skipsLinesThatHaveNoWordsToShow() {
        val graph = GalaxyGraph(stars, listOf(GalaxyLine("a", "b", 2), title("a", "c", "t1", "t2")))
        assertEquals(listOf("c"), GalaxyNeighbours.of(graph, "a").map { it.star.id })
    }

    @Test fun aConceptLineThatCarriesWordsIsShownAsAConceptNeighbour() {
        val graph = GalaxyGraph(stars, listOf(GalaxyLine("a", "b", 1, GalaxyLineKind.CONCEPT, listOf("trump", "energy"))))
        val only = GalaxyNeighbours.of(graph, "a").single()
        assertEquals(GalaxyLineKind.CONCEPT, only.kind)
        assertEquals(listOf("trump", "energy"), only.sharedTerms)
    }

    @Test fun unknownStarAndIsolatedStarGiveNothing() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "t1", "t2")))
        assertTrue(GalaxyNeighbours.of(graph, "ghost").isEmpty())
        assertTrue(GalaxyNeighbours.of(graph, "e").isEmpty())
        assertTrue(GalaxyNeighbours.of(GalaxyGraph.EMPTY, "a").isEmpty())
    }

    @Test fun neverReturnsTheStarItselfOrAStarMissingFromTheGraph() {
        val graph = GalaxyGraph(listOf(star("a")), listOf(title("a", "a", "t1", "t2"), title("a", "ghost", "t3", "t4")))
        assertTrue(GalaxyNeighbours.of(graph, "a").isEmpty())
    }

    @Test fun aNonPositiveLimitGivesNothing() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "t1", "t2")))
        assertTrue(GalaxyNeighbours.of(graph, "a", limit = 0).isEmpty())
        assertTrue(GalaxyNeighbours.of(graph, "a", limit = -3).isEmpty())
    }
}
