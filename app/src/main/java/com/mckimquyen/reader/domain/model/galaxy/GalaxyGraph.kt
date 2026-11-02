package com.mckimquyen.reader.domain.model.galaxy

/** Metadata needed to render and reopen one read article. */
data class GalaxyStar(
    val id: String,
    val title: String,
    val feedName: String,
    val date: Long,
    val concepts: Set<String>,
)

data class GalaxyLine(val a: String, val b: String, val sharedConcepts: Int)

data class GalaxyGraph(val stars: List<GalaxyStar>, val lines: List<GalaxyLine>) {
    companion object {
        val EMPTY = GalaxyGraph(emptyList(), emptyList())
    }
}
