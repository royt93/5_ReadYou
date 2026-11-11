package com.mckimquyen.reader.domain.sv

import android.util.Log
import com.mckimquyen.reader.domain.model.rpg.AiQuizParse
import com.mckimquyen.reader.domain.model.rpg.AiQuizPolicy
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

enum class AiQuizFailure { NOTHING_TO_ASK, NO_KEY, NETWORK, REJECTED }

sealed interface AiQuizResult {
    data class Ready(val quiz: QuizQuestion) : AiQuizResult
    data class Failed(val reason: AiQuizFailure) : AiQuizResult
}

/**
 * Writes one quiz question with Gemini (RPG-02). Privacy boundary: the only article data that can leave the device is
 * the title and a preview of at most [AiQuizPolicy.MAX_PREVIEW_CHARS]; this class is handed those strings, never an
 * article. It does not check the user's consent: the caller (BrainRpgViewModel) is the single place that decides
 * whether to call, and it only calls when the consent is `true`.
 */
@Singleton
class AiQuizService @Inject constructor(
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {

    suspend fun generate(
        articleId: String,
        title: String,
        shortDescription: String,
        category: String,
        languageTag: String,
    ): AiQuizResult {
        val cleanTitle = title.trim()
        val preview = AiQuizPolicy.sanitizePreview(shortDescription)
        if (!AiQuizPolicy.hasContentToAsk(cleanTitle, preview)) return AiQuizResult.Failed(AiQuizFailure.NOTHING_TO_ASK)
        val keys = summaryService.resolveApiKeys()
        if (keys.isEmpty()) return AiQuizResult.Failed(AiQuizFailure.NO_KEY)

        val body = AiQuizPolicy.buildRequestBody(AiQuizPolicy.buildPrompt(cleanTitle, preview, languageTag))
        val raw = try {
            gateway.execute(AiQuizPolicy.USE_CASE, body, keys)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiSummaryService.SummaryException) {
            // MissingApiKey is an object inside a sealed SummaryException, so it is matched by identity.
            // Only the exception type is logged: a message could carry the title or the preview.
            Log.w(TAG, "AI quiz request failed: ${e.javaClass.simpleName}")
            return AiQuizResult.Failed(
                if (e === GeminiSummaryService.SummaryException.MissingApiKey) AiQuizFailure.NO_KEY else AiQuizFailure.NETWORK
            )
        } catch (e: Exception) {
            Log.w(TAG, "AI quiz request failed: ${e.javaClass.simpleName}")
            return AiQuizResult.Failed(AiQuizFailure.NETWORK)
        }
        return when (val parsed = AiQuizPolicy.parseAndValidate(raw, articleId, category)) {
            is AiQuizParse.Valid -> AiQuizResult.Ready(parsed.quiz)
            is AiQuizParse.Rejected -> {
                Log.w(TAG, "AI quiz answer rejected: ${parsed.reason}")
                AiQuizResult.Failed(AiQuizFailure.REJECTED)
            }
        }
    }

    private companion object {
        const val TAG = "AiQuizService"
    }
}
