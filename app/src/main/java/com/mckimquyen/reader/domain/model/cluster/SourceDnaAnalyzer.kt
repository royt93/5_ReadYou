package com.mckimquyen.reader.domain.model.cluster

import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import java.net.URI

internal enum class SourceDnaRelation { EARLIEST_IN_CLUSTER, HIGH_SIMILARITY, NO_CLEAR_RELATIONSHIP }

internal data class SourceDnaNode(
    val article: ArticleWithFeed,
    val parentArticleId: String?,
    val relation: SourceDnaRelation,
    val similarityToParent: Float?,
    val order: Int,
    val sourceLabel: String?,
)

internal data class SourceDnaResult(
    val nodes: List<SourceDnaNode>,
    val isEarliestAmbiguous: Boolean,
    val analyzedArticleCount: Int,
    val wasTruncated: Boolean,
    val hasAnySimilarityEdge: Boolean,
)

/** On-demand lexical/time heuristic; never treats a feed timestamp as verified provenance. */
internal object SourceDnaAnalyzer {
    const val MAX_ARTICLES = 20
    const val HIGH_SIMILARITY_THRESHOLD = 0.75f

    fun analyze(
        articles: List<ArticleWithFeed>,
        similarity: (ArticleWithFeed, ArticleWithFeed) -> Float,
    ): SourceDnaResult {
        val deduplicated = articles.withIndex()
            .filter { it.value.article.id.isNotBlank() }
            .groupBy { it.value.article.id }
            .values
            .map { group -> group.minWith(compareBy<IndexedValue<ArticleWithFeed>> { it.value.article.date.time }.thenBy { it.index }) }
            .sortedWith(compareBy<IndexedValue<ArticleWithFeed>> { it.value.article.date.time }.thenBy { it.value.article.id })
        val truncated = deduplicated.size > MAX_ARTICLES
        val sorted = deduplicated.take(MAX_ARTICLES).map { it.value }
        val earliestTime = sorted.firstOrNull()?.article?.date?.time
        val earliestAmbiguous = earliestTime != null && sorted.count { it.article.date.time == earliestTime } != 1
        var edgeCount = 0
        val nodes = sorted.mapIndexed { index, candidate ->
            var bestParent: ArticleWithFeed? = null
            var bestScore: Float? = null
            for (parentIndex in 0 until index) {
                val parent = sorted[parentIndex]
                if (parent.article.date.time >= candidate.article.date.time) continue
                val score = similarity(parent, candidate)
                if (!score.isFinite() || score !in 0f..1f) continue
                val current = bestScore
                if (current == null || score > current ||
                    (score == current && (parent.article.date.time < requireNotNull(bestParent).article.date.time ||
                        (parent.article.date.time == bestParent?.article?.date?.time && parent.article.id < bestParent?.article?.id.orEmpty())))
                ) {
                    bestParent = parent
                    bestScore = score
                }
            }
            val linked = bestScore?.let { it >= HIGH_SIMILARITY_THRESHOLD } == true
            if (linked) edgeCount++
            val isUniqueEarliest = index == 0 && !earliestAmbiguous
            SourceDnaNode(
                article = candidate,
                parentArticleId = bestParent?.article?.id.takeIf { linked },
                relation = when {
                    isUniqueEarliest -> SourceDnaRelation.EARLIEST_IN_CLUSTER
                    linked -> SourceDnaRelation.HIGH_SIMILARITY
                    else -> SourceDnaRelation.NO_CLEAR_RELATIONSHIP
                },
                similarityToParent = bestScore.takeIf { linked },
                order = index,
                sourceLabel = candidate.feed.name.takeIf { it.isNotBlank() } ?: candidate.article.link.hostOrNull(),
            )
        }
        return SourceDnaResult(
            nodes = nodes,
            isEarliestAmbiguous = earliestAmbiguous,
            analyzedArticleCount = sorted.size,
            wasTruncated = truncated,
            hasAnySimilarityEdge = edgeCount > 0,
        )
    }

    private fun String.hostOrNull(): String? = runCatching { URI(this).host?.takeIf(String::isNotBlank) }.getOrNull()
}
