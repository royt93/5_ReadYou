package com.mckimquyen.reader.domain.model.secondbrain

import java.util.Locale

/**
 * Pure, deterministic graph construction. Concepts are nodes; two concepts are linked when they
 * appear in the same article. Output is capped at [MAX_NODES] by weight so rendering stays bounded.
 */
object BrainGraphBuilder {
    const val MAX_NODES = 200

    /** 200 nodes can imply ~20k pairs; every edge is a canvas line, so keep only the strongest. */
    const val MAX_EDGES = 300

    fun build(
        articles: List<BrainArticle>,
        fromTime: Long = Long.MIN_VALUE,
        toTime: Long = Long.MAX_VALUE,
    ): BrainGraph {
        if (articles.isEmpty()) return BrainGraph.EMPTY

        val minTime = articles.minOf { it.date }
        val maxTime = articles.maxOf { it.date }
        val inWindow = articles
            .filter { it.date in fromTime..toTime }
            .map { it.copy(concepts = it.concepts.mapNotNullTo(linkedSetOf()) { c -> normalize(c) }) }
            .filter { it.concepts.isNotEmpty() }
            .sortedWith(compareBy({ it.date }, { it.id }))

        val byConcept = linkedMapOf<String, MutableList<BrainArticle>>()
        inWindow.forEach { article ->
            article.concepts.forEach { concept -> byConcept.getOrPut(concept) { mutableListOf() }.add(article) }
        }

        val kept = byConcept.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableList<BrainArticle>>> { it.value.size }.thenBy { it.key })
            .take(MAX_NODES)
            .sortedBy { it.key }
        val keptIds = kept.mapTo(hashSetOf()) { it.key }

        val nodes = kept.map { (concept, list) ->
            BrainNode(
                id = concept,
                label = displayLabel(concept),
                weight = list.size,
                articleIds = list.map { it.id },
                firstSeen = list.minOf { it.date },
                lastSeen = list.maxOf { it.date },
            )
        }

        val pairCounts = hashMapOf<Pair<String, String>, Int>()
        inWindow.forEach { article ->
            val present = article.concepts.filter { it in keptIds }.sorted()
            for (i in present.indices) for (j in i + 1 until present.size) {
                pairCounts.merge(present[i] to present[j], 1, Int::plus)
            }
        }
        val edges = pairCounts.entries
            .map { (pair, count) -> BrainEdge(pair.first, pair.second, count) }
            .sortedWith(compareByDescending<BrainEdge> { it.weight }.thenBy { it.from }.thenBy { it.to })
            .take(MAX_EDGES)
            .sortedWith(compareBy({ it.from }, { it.to }))

        val referenced = nodes.flatMapTo(hashSetOf()) { it.articleIds }
        val info = inWindow
            .filter { it.id in referenced }
            .associate { it.id to BrainArticleInfo(it.id, it.title, it.date) }

        return BrainGraph(nodes, edges, minTime, maxTime, info)
    }

    private fun normalize(concept: String): String? =
        concept.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
}
