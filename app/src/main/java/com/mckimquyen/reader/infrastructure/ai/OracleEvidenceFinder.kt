package com.mckimquyen.reader.infrastructure.ai

import android.util.Log
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.domain.oracle.OracleEvidenceCandidate
import com.mckimquyen.reader.domain.oracle.OracleEvidencePolicy
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds up to [OracleEvidencePolicy.MAX_EVIDENCE] saved articles that may say how a due bet turned out (ORACLE-03). Purely
 * local: it ranks the newest saved articles (list columns only, never a body) against the bet's own question with the
 * on-device search engine. Nothing leaves the device, and the result is a hint, not a verdict.
 */
@Singleton
class OracleEvidenceFinder @Inject constructor(
    private val articleDao: ArticleDao,
    private val searchEngine: SemanticSearchEngine,
) {

    suspend fun find(accountId: Int, bet: OracleBet): List<OracleEvidence> = try {
        val recent = articleDao.queryRecentArticleListWithFeed(accountId, OracleEvidencePolicy.RECENT_SCAN_LIMIT)
            .map { it.toArticleWithFeed() }
        val ranked = searchEngine.rank(
            query = bet.question,
            articles = recent,
            minScoreThreshold = SemanticSearchEngine.DEFAULT_MIN_SCORE_THRESHOLD,
            limit = OracleEvidencePolicy.RECENT_SCAN_LIMIT,
        )
        OracleEvidencePolicy.select(
            candidates = ranked.map {
                OracleEvidenceCandidate(
                    articleId = it.articleWithFeed.article.id,
                    title = it.articleWithFeed.article.title,
                    feedName = it.articleWithFeed.feed.name,
                    dateMillis = it.articleWithFeed.article.date.time,
                    score = it.score,
                )
            },
            placedAt = bet.placedAt,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Only the exception type: a message could carry the question.
        Log.w(TAG, "Evidence search failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    private companion object {
        const val TAG = "OracleEvidenceFinder"
    }
}
