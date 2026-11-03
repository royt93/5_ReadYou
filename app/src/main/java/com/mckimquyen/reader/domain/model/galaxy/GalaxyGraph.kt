package com.mckimquyen.reader.domain.model.galaxy

/** Metadata needed to render and reopen one read article. */
data class GalaxyStar(
    val id: String,
    val title: String,
    val feedName: String,
    val date: Long,
    val concepts: Set<String>,
)

/** Why two stars are connected: a shared concept, or rare words their headlines have in common. */
enum class GalaxyLineKind { CONCEPT, TITLE_WORDS }

/** [sharedTerms] names the headline words two stars have in common (sorted); it may be set on a CONCEPT line too. */
data class GalaxyLine(
    val a: String,
    val b: String,
    val sharedConcepts: Int,
    val kind: GalaxyLineKind = GalaxyLineKind.CONCEPT,
    val sharedTerms: List<String> = emptyList(),
)

data class GalaxyGraph(val stars: List<GalaxyStar>, val lines: List<GalaxyLine>) {
    companion object {
        val EMPTY = GalaxyGraph(emptyList(), emptyList())
    }
}
