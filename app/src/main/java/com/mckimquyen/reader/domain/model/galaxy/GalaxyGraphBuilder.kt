package com.mckimquyen.reader.domain.model.galaxy

import java.util.Locale

/**
 * Pure, deterministic galaxy construction: one star per read article, a line between two stars that share
 * at least one concept. Output is bounded so the canvas never draws more than [MAX_STARS] + [MAX_LINES] shapes.
 */
object GalaxyGraphBuilder {
    const val MAX_STARS = 600

    /** Every line is a canvas draw call, so keep only the strongest. */
    const val MAX_LINES = 400

    /** A concept shared by hundreds of articles would imply O(n²) pairs; link each star to its nearest by date only. */
    const val MAX_NEIGHBORS_PER_STAR = 3

    private val byDateThenId = compareBy<GalaxyStar>({ it.date }, { it.id })

    fun build(
        articles: List<GalaxyStar>,
        from: Long = Long.MIN_VALUE,
        to: Long = Long.MAX_VALUE,
    ): GalaxyGraph {
        val stars = articles
            .filter { it.date in from..to }
            .map { star -> star.copy(concepts = star.concepts.mapNotNullTo(linkedSetOf(), ::normalize)) }
            .sortedWith(byDateThenId.reversed())
            .take(MAX_STARS)
            .sortedWith(byDateThenId)
        if (stars.isEmpty()) return GalaxyGraph.EMPTY

        val byConcept = linkedMapOf<String, MutableList<GalaxyStar>>()
        stars.forEach { star -> star.concepts.forEach { byConcept.getOrPut(it) { mutableListOf() }.add(star) } }

        val pairCounts = hashMapOf<Pair<String, String>, Int>()
        byConcept.values.forEach { members ->
            for (i in members.indices) {
                for (j in i + 1..minOf(i + MAX_NEIGHBORS_PER_STAR, members.lastIndex)) {
                    pairCounts.merge(members[i].id to members[j].id, 1, Int::plus)
                }
            }
        }
        val lines = pairCounts.entries
            .map { (pair, count) -> GalaxyLine(pair.first, pair.second, count) }
            .sortedWith(compareByDescending<GalaxyLine> { it.sharedConcepts }.thenBy { it.a }.thenBy { it.b })
            .take(MAX_LINES)
        return GalaxyGraph(stars, lines)
    }

    private fun normalize(concept: String): String? =
        concept.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
}
