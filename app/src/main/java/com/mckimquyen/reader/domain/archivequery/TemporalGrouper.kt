package com.mckimquyen.reader.domain.archivequery

import java.time.YearMonth

/**
 * Accumulates [RankedArchiveArticle]s into calendar-month buckets,
 * keeping a total unique-article count and only the top-3 candidates per month.
 */
class TemporalGrouper(private val maxCandidates: Int = DEFAULT_MAX_CANDIDATES) {

    private val buckets = mutableMapOf<YearMonth, MutableBucket>()

    fun add(month: YearMonth, article: RankedArchiveArticle) {
        val bucket = buckets.getOrPut(month) { MutableBucket() }
        bucket.seenIds.add(article.articleId)
        bucket.insertCandidate(article, maxCandidates)
    }

    /** Returns an immutable snapshot of all accumulated months. */
    fun result(): Map<YearMonth, ArchiveMonth> = buckets.mapValues { (ym, b) ->
        ArchiveMonth(
            year = ym.year,
            month = ym.monthValue,
            articleCount = b.seenIds.size,
            topCandidates = b.candidates.toList(),
        )
    }

    private class MutableBucket {
        val seenIds = mutableSetOf<String>()
        /** Sorted best-first: score DESC, date DESC, id DESC. Always at most [maxCandidates]. */
        val candidates = mutableListOf<RankedArchiveArticle>()

        fun insertCandidate(article: RankedArchiveArticle, max: Int) {
            val existingIdx = candidates.indexOfFirst { it.articleId == article.articleId }
            if (existingIdx >= 0) {
                // If the new version has higher or equal priority, update it in place; otherwise ignore
                if (COMPARATOR.compare(article, candidates[existingIdx]) < 0) {
                    candidates.removeAt(existingIdx)
                } else {
                    return
                }
            }
            // Find insertion index using the comparator (best first)
            val idx = candidates.indexOfFirst { existing -> COMPARATOR.compare(article, existing) < 0 }
            if (idx < 0) {
                if (candidates.size < max) candidates.add(article)
            } else {
                candidates.add(idx, article)
                if (candidates.size > max) candidates.removeAt(candidates.lastIndex)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_CANDIDATES = 3

        /** Best article first: highest score, then newest date, then largest id. */
        private val COMPARATOR = compareByDescending<RankedArchiveArticle> { it.score }
            .thenByDescending { it.date }
            .thenByDescending { it.articleId }
    }
}
