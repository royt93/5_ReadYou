package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.domain.ai.agents.OracleClaimPolicy
import com.mckimquyen.reader.domain.ai.agents.OracleParse
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.domain.repository.OracleCandidateRow
import com.mckimquyen.reader.domain.repository.OracleMarketDao
import com.mckimquyen.reader.ui.ext.oracleClaimsGeminiConsent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class OracleRunResult(val requests: Int, val claims: Int, val stopped: Boolean)

/**
 * Background prediction finder (ORACLE-01). Privacy boundary: the only article data that can leave the device is a title
 * and a short description (see [OracleClaimPolicy.MAX_INPUT_CHARS]); this class reads exactly those two columns through
 * [OracleMarketDao.candidates] and runs only when the user's own, separate consent is exactly `true`.
 */
@Singleton
open class OracleClaimExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OracleMarketDao,
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {

    protected open fun isConsented(): Boolean = context.oracleClaimsGeminiConsent == true

    protected open fun nowMillis(): Long = System.currentTimeMillis()

    suspend fun extractRecent(accountId: Int): OracleRunResult {
        val now = nowMillis()
        cleanUp(now)
        if (!isConsented()) return OracleRunResult(requests = 0, claims = 0, stopped = false)
        val keys = summaryService.resolveApiKeys()
        if (keys.isEmpty()) return OracleRunResult(requests = 0, claims = 0, stopped = true)

        val since = now - TimeUnit.DAYS.toMillis(OracleClaimPolicy.RECENT_WINDOW_DAYS.toLong())
        val candidates = dao.candidates(accountId, since, OracleClaimPolicy.MAX_ARTICLES_PER_RUN)
        val languageTag = Locale.getDefault().toLanguageTag()
        var requests = 0
        var claims = 0
        for (candidate in candidates) {
            val input = OracleClaimPolicy.sanitizeInput(candidate.title, candidate.shortDescription)
            if (!OracleClaimPolicy.hasContentToAnalyze(input)) {
                dao.upsert(
                    OracleMarket(articleId = candidate.id, status = OracleMarketStatus.NO_CLAIM, attempts = candidate.attempts, createdAt = now)
                )
                continue
            }
            val body = OracleClaimPolicy.buildRequestBody(OracleClaimPolicy.buildPrompt(input, languageTag))
            requests++
            val raw = try {
                gateway.execute(OracleClaimPolicy.USE_CASE, body, keys)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Only the exception type: a message could carry the title or the description.
                Log.w(TAG, "Prediction request failed: ${e.javaClass.simpleName}")
                return OracleRunResult(requests, claims, stopped = true)
            }
            if (record(candidate, OracleClaimPolicy.parseAndValidate(raw, now), now)) claims++
        }
        return OracleRunResult(requests, claims, stopped = false)
    }

    /** Returns true when a usable claim was stored. */
    private suspend fun record(candidate: OracleCandidateRow, parsed: OracleParse, now: Long): Boolean {
        when (parsed) {
            is OracleParse.Claim -> dao.upsert(
                OracleMarket(
                    articleId = candidate.id,
                    status = OracleMarketStatus.HAS_CLAIM,
                    attempts = candidate.attempts,
                    question = parsed.question,
                    expiresAt = parsed.expiresAt,
                    resolutionCriteria = parsed.resolutionCriteria,
                    createdAt = now,
                )
            )
            OracleParse.NoClaim -> dao.upsert(
                OracleMarket(articleId = candidate.id, status = OracleMarketStatus.NO_CLAIM, attempts = candidate.attempts, createdAt = now)
            )
            is OracleParse.Rejected -> {
                Log.w(TAG, "Prediction answer rejected: ${parsed.reason}")
                val attempts = candidate.attempts + 1
                val status = if (attempts >= OracleClaimPolicy.MAX_ATTEMPTS) OracleMarketStatus.NO_CLAIM else OracleMarketStatus.PENDING
                dao.upsert(OracleMarket(articleId = candidate.id, status = status, attempts = attempts, createdAt = now))
            }
        }
        return parsed is OracleParse.Claim
    }

    private suspend fun cleanUp(now: Long) {
        try {
            dao.deleteStale(now - TimeUnit.DAYS.toMillis(OracleClaimPolicy.RETENTION_DAYS.toLong()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Prediction cleanup failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "OracleClaimExtractor"
    }
}
