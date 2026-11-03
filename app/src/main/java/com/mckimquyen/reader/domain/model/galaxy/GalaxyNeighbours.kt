package com.mckimquyen.reader.domain.model.galaxy

/** One star related to the selected star, plus the words that justify showing it. */
data class GalaxyNeighbour(val star: GalaxyStar, val sharedTerms: List<String>, val kind: GalaxyLineKind)

/** Pure lookup used by the star card; keeps the Compose code free of graph logic. */
object GalaxyNeighbours {
    fun of(
        graph: GalaxyGraph,
        starId: String,
        limit: Int = GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR,
    ): List<GalaxyNeighbour> {
        if (limit <= 0) return emptyList()
        val byId = graph.stars.associateBy { it.id }
        return graph.lines.asSequence()
            .filter { it.sharedTerms.isNotEmpty() && (it.a == starId || it.b == starId) }
            .mapNotNull { line ->
                val otherId = if (line.a == starId) line.b else line.a
                if (otherId == starId) return@mapNotNull null
                byId[otherId]?.let { GalaxyNeighbour(it, line.sharedTerms, line.kind) }
            }
            .take(limit)
            .toList()
    }
}
