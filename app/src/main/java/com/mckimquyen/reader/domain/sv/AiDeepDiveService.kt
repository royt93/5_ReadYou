package com.mckimquyen.reader.domain.sv

import android.util.Log
import com.mckimquyen.reader.domain.ai.agents.DeepDiveParse
import com.mckimquyen.reader.domain.ai.agents.DeepDivePolicy
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

enum class AiDeepDiveFailure { NOTHING_TO_ANALYZE, NO_KEY, NETWORK, REJECTED }

sealed interface AiDeepDiveResult {
    data class Ready(val report: DeepDiveReport) : AiDeepDiveResult
    data class Failed(val reason: AiDeepDiveFailure) : AiDeepDiveResult
}

/**
 * Writes one Deep Dive report with Gemini (FRONT-01) in a single request. Privacy boundary: the only article data that
 * can leave the device is the title and an excerpt of at most [DeepDivePolicy.MAX_EXCERPT_CHARS]; this class is handed
 * those strings, never a whole record. It does not check the user's consent: DeepDiveViewModel is the single place that
 * decides whether to call, and it only calls when the consent is `true`.
 */
@Singleton
class AiDeepDiveService @Inject constructor(
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {

    suspend fun generate(articleId: String, title: String, bodyText: String, languageTag: String): AiDeepDiveResult {
        val cleanTitle = title.trim()
        val excerpt = DeepDivePolicy.sanitizeExcerpt(bodyText)
        if (!DeepDivePolicy.hasContentToAnalyze(cleanTitle, excerpt)) {
            return AiDeepDiveResult.Failed(AiDeepDiveFailure.NOTHING_TO_ANALYZE)
        }
        val keys = summaryService.resolveApiKeys()
        if (keys.isEmpty()) return AiDeepDiveResult.Failed(AiDeepDiveFailure.NO_KEY)

        val body = DeepDivePolicy.buildRequestBody(DeepDivePolicy.buildPrompt(cleanTitle, excerpt, languageTag))
        val raw = try {
            gateway.execute(DeepDivePolicy.USE_CASE, body, keys)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiSummaryService.SummaryException) {
            // MissingApiKey is an object inside a sealed SummaryException, so it is matched by identity.
            // Only the exception type is logged: a message could carry the title or the excerpt.
            Log.w(TAG, "Deep Dive request failed: ${e.javaClass.simpleName}")
            return AiDeepDiveResult.Failed(
                if (e === GeminiSummaryService.SummaryException.MissingApiKey) AiDeepDiveFailure.NO_KEY else AiDeepDiveFailure.NETWORK
            )
        } catch (e: Exception) {
            Log.w(TAG, "Deep Dive request failed: ${e.javaClass.simpleName}")
            return AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK)
        }
        return when (val parsed = DeepDivePolicy.parseAndValidate(raw, articleId)) {
            is DeepDiveParse.Valid -> AiDeepDiveResult.Ready(parsed.report)
            is DeepDiveParse.Rejected -> {
                Log.w(TAG, "Deep Dive answer rejected: ${parsed.reason}")
                AiDeepDiveResult.Failed(AiDeepDiveFailure.REJECTED)
            }
        }
    }

    private companion object {
        const val TAG = "AiDeepDiveService"
    }
}
