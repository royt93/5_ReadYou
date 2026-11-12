package com.mckimquyen.reader.domain.oracle

data class OracleEvidenceCandidate(
    val articleId: String,
    val title: String,
    val feedName: String,
    val dateMillis: Long,
    val score: Float,
)

data class OracleEvidence(
    val articleId: String,
    val title: String,
    val feedName: String,
    val dateMillis: Long,
)

/**
 * Pure rules for which saved articles are offered as hints when a bet is due (ORACLE-03). Only an article published at
 * or after the moment the bet was placed can say anything about what happened afterwards.
 */
object OracleEvidencePolicy {
    const val MAX_EVIDENCE = 3
    const val RECENT_SCAN_LIMIT = 200

    fun select(candidates: List<OracleEvidenceCandidate>, placedAt: Long, limit: Int = MAX_EVIDENCE): List<OracleEvidence> {
        if (limit <= 0) return emptyList()
        return candidates
            .filter { it.dateMillis >= placedAt }
            .groupBy { it.articleId }
            .map { (_, sameArticle) -> sameArticle.maxWith(compareBy({ it.score }, { it.dateMillis })) }
            .sortedWith(
                compareByDescending<OracleEvidenceCandidate> { it.score }
                    .thenByDescending { it.dateMillis }
                    .thenBy { it.articleId },
            )
            .take(limit)
            .map { OracleEvidence(it.articleId, it.title, it.feedName, it.dateMillis) }
    }
}
