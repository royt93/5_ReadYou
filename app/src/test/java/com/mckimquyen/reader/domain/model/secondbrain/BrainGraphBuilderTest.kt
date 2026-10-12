package com.mckimquyen.reader.domain.model.secondbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainGraphBuilderTest {

    private fun article(id: String, day: Long, vararg concepts: String) =
        BrainArticle(id = id, title = "Title $id", date = day * DAY, concepts = concepts.toSet())

    @Test
    fun build_noArticles_returnsEmptyGraph() {
        assertEquals(BrainGraph.EMPTY, BrainGraphBuilder.build(emptyList()))
    }

    @Test
    fun build_articlesWithoutConcepts_returnsEmptyGraph() {
        val graph = BrainGraphBuilder.build(listOf(article("a1", 1), article("a2", 2, " ", "")))
        assertTrue(graph.nodes.isEmpty())
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun build_mergesConceptsCaseInsensitively_andCountsDistinctArticles() {
        val graph = BrainGraphBuilder.build(
            listOf(article("a1", 1, "AI", "ai"), article("a2", 2, "Ai"), article("a3", 3, "Chips")),
        )

        val ai = graph.nodes.single { it.id == "ai" }
        assertEquals(2, ai.weight)
        assertEquals(listOf("a1", "a2"), ai.articleIds)
        assertEquals(1 * DAY, ai.firstSeen)
        assertEquals(2 * DAY, ai.lastSeen)
        assertEquals(2, graph.nodes.size)
    }

    @Test
    fun build_connectsConceptsSharingAnArticle_withoutSelfLoopsOrDuplicates() {
        val graph = BrainGraphBuilder.build(
            listOf(article("a1", 1, "ai", "chips"), article("a2", 2, "chips", "ai"), article("a3", 3, "energy")),
        )

        assertEquals(1, graph.edges.size)
        val edge = graph.edges.single()
        assertEquals("ai", edge.from)
        assertEquals("chips", edge.to)
        assertEquals(2, edge.weight)
        assertFalse(graph.edges.any { it.from == it.to })
    }

    @Test
    fun build_timeWindow_onlyKeepsArticlesInsideInclusiveRange() {
        val articles = listOf(article("a1", 1, "ai"), article("a2", 5, "ai", "chips"), article("a3", 9, "chips"))

        val graph = BrainGraphBuilder.build(articles, fromTime = 5 * DAY, toTime = 9 * DAY)

        assertEquals(listOf("a2"), graph.nodes.single { it.id == "ai" }.articleIds)
        assertEquals(listOf("a2", "a3"), graph.nodes.single { it.id == "chips" }.articleIds)
    }

    @Test
    fun build_reportsTimeBoundsOfUnfilteredData_soASliderCanSpanTheFullRange() {
        val articles = listOf(article("a1", 2, "ai"), article("a2", 8, "ai"))

        val graph = BrainGraphBuilder.build(articles, fromTime = 7 * DAY, toTime = 8 * DAY)

        assertEquals(2 * DAY, graph.minTime)
        assertEquals(8 * DAY, graph.maxTime)
    }

    @Test
    fun build_capsNodes_keepsHeaviestAndDropsEdgesToRemovedNodes() {
        val many = (1..BrainGraphBuilder.MAX_NODES + 20).map { n ->
            article("a$n", n.toLong(), "c$n", "hub")
        }

        val graph = BrainGraphBuilder.build(many)

        assertEquals(BrainGraphBuilder.MAX_NODES, graph.nodes.size)
        assertTrue("hub has the highest weight and must survive", graph.nodes.any { it.id == "hub" })
        val ids = graph.nodes.map { it.id }.toSet()
        assertTrue(graph.edges.all { it.from in ids && it.to in ids })
    }

    @Test
    fun build_isDeterministic_whenWeightsTie() {
        val articles = listOf(article("a1", 1, "zeta", "alpha"), article("a2", 2, "beta"))

        val first = BrainGraphBuilder.build(articles)
        val second = BrainGraphBuilder.build(articles.reversed())

        assertEquals(first.nodes.map { it.id }, second.nodes.map { it.id })
        assertEquals(listOf("alpha", "beta", "zeta"), first.nodes.map { it.id })
    }

    @Test
    fun build_exposesTitleAndDateForEveryArticleOnAKeptNode() {
        val graph = BrainGraphBuilder.build(listOf(article("a1", 3, "ai"), article("a2", 4, "chips")))

        assertEquals("Title a1", graph.articleInfo.getValue("a1").title)
        assertEquals(3 * DAY, graph.articleInfo.getValue("a1").date)
        assertEquals(setOf("a1", "a2"), graph.articleInfo.keys)
    }

    @Test
    fun build_articleInfo_excludesArticlesOutsideTheWindow() {
        val graph = BrainGraphBuilder.build(
            listOf(article("a1", 1, "ai"), article("a2", 9, "ai")),
            fromTime = 5 * DAY,
        )
        assertEquals(setOf("a2"), graph.articleInfo.keys)
    }

    @Test
    fun displayLabel_humanisesConceptIds() {
        assertEquals("Artificial intelligence", displayLabel("artificial_intelligence"))
        assertEquals("Ai", displayLabel("ai"))
        assertEquals("", displayLabel("  "))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
