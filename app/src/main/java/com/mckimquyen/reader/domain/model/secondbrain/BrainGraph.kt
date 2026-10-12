package com.mckimquyen.reader.domain.model.secondbrain

/** One article reduced to what the graph needs: when it was published and which concepts it touches. */
data class BrainArticle(
    val id: String,
    val title: String,
    val date: Long,
    val concepts: Set<String>,
)

data class BrainNode(
    val id: String,
    val label: String,
    /** Distinct articles that mention this concept. */
    val weight: Int,
    val articleIds: List<String>,
    val firstSeen: Long,
    val lastSeen: Long,
)

data class BrainEdge(
    val from: String,
    val to: String,
    /** Distinct articles mentioning both concepts. */
    val weight: Int,
)

/** Title and date for an article referenced by a node, so the detail panel needs no extra lookup. */
data class BrainArticleInfo(val id: String, val title: String, val date: Long)

data class BrainGraph(
    val nodes: List<BrainNode>,
    val edges: List<BrainEdge>,
    /** Time range of ALL input articles, ignoring any window filter, so a slider can span the full history. */
    val minTime: Long,
    val maxTime: Long,
    /** Info for every article id referenced by [nodes], keyed by id. */
    val articleInfo: Map<String, BrainArticleInfo> = emptyMap(),
) {
    companion object {
        val EMPTY = BrainGraph(emptyList(), emptyList(), 0L, 0L)
    }
}

/** "artificial_intelligence" -> "Artificial intelligence"; blank stays blank. */
fun displayLabel(conceptId: String): String =
    conceptId.trim().replace('_', ' ').replaceFirstChar { it.uppercase() }
