package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class GalaxyLayoutTest {

    private fun star(id: String, vararg concepts: String) =
        GalaxyStar(id, "Title $id", "Feed", 0L, concepts.toSet())

    private fun dist(a: GalaxyPoint, b: GalaxyPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    @Test
    fun compute_emptyInputGivesEmptyMap() {
        assertTrue(GalaxyLayout.compute(emptyList()).isEmpty())
    }

    @Test
    fun compute_isStableByIdWhateverTheInputOrderOrCompany() {
        val input = listOf(star("a", "ai"), star("b", "ai"), star("c", "news"))
        val forward = GalaxyLayout.compute(input)
        assertEquals(forward, GalaxyLayout.compute(input.reversed()))
        // A star keeps its place when other stars appear, so the galaxy does not reshuffle on every refresh.
        assertEquals(forward["a"], GalaxyLayout.compute(input + star("z", "news"))["a"])
    }

    @Test
    fun compute_staysFiniteAndInsideTheBounds() {
        val stars = (0 until 300).map { star("id-$it", "c${it % 9}") } + star("none")
        GalaxyLayout.compute(stars).values.forEach { p ->
            assertTrue(p.x.isFinite() && p.y.isFinite() && p.z.isFinite())
            assertTrue(p.x in -GalaxyLayout.RADIUS..GalaxyLayout.RADIUS)
            assertTrue(p.y in -GalaxyLayout.RADIUS..GalaxyLayout.RADIUS)
            assertTrue(p.z in -GalaxyLayout.GALAXY_THICKNESS..GalaxyLayout.GALAXY_THICKNESS)
        }
    }

    @Test
    fun compute_givesEveryStarAPoint() {
        val stars = (0 until 50).map { star("s$it", "x") }
        assertEquals(stars.map { it.id }.toSet(), GalaxyLayout.compute(stars).keys)
    }

    @Test
    fun compute_putsStarsOfTheSameConceptCloserThanStarsOfOtherConcepts() {
        val ai = (0 until 20).map { star("ai-$it", "ai") }
        val sport = (0 until 20).map { star("sport-$it", "sport") }
        val positions = GalaxyLayout.compute(ai + sport)

        // Every pair sharing a concept sits inside one cluster, whatever the ids. Hash-scattered stars would
        // spread over the whole disc (diameter ~2) and break this bound.
        val widest = ai.flatMap { a -> ai.map { b -> dist(positions.getValue(a.id), positions.getValue(b.id)) } }.max()
        assertTrue("widest=$widest", widest <= GalaxyLayout.MAX_CLUSTER_DIAMETER)
        val widestSport = sport.flatMap { a -> sport.map { b -> dist(positions.getValue(a.id), positions.getValue(b.id)) } }.max()
        assertTrue("widestSport=$widestSport", widestSport <= GalaxyLayout.MAX_CLUSTER_DIAMETER)
    }
}
