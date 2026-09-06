package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gọi Google Gemini REST API để tóm tắt và trích xuất điểm nhấn bài báo (AI TL;DR & Highlights).
 *
 * Key lấy từ [GeminiConfig.API_KEYS] (class constant). Hỗ trợ FAILOVER: thử lần lượt từng key,
 * nếu một key lỗi (sai key 400/403 hoặc hết quota 429, hoặc server lỗi) thì tự chuyển sang key
 * kế tiếp trong danh sách. Nếu hết key hoặc lỗi mạng, tự động fallback sang thuật toán heuristic
 * ngoại tuyến [ArticleHighlightsExtractor] để đảm bảo trải nghiệm người dùng luôn mượt mà.
 */
@Singleton
class GeminiSummaryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    /**
     * Exception phân loại theo TYPE (không chứa chuỗi hiển thị cho người dùng) để lớp UI tự
     * map sang string resource đa ngôn ngữ.
     */
    sealed class SummaryException : Exception() {
        object MissingApiKey : SummaryException()
        object EmptyContent : SummaryException()
        object InvalidApiKey : SummaryException()      // HTTP 400 / 403
        object RateLimited : SummaryException()         // HTTP 429
        data class Http(val code: Int) : SummaryException()
        object EmptyResponse : SummaryException()
        object ParseError : SummaryException()
        object Network : SummaryException()
    }

    /**
     * Trích xuất cấu trúc điểm nhấn [ArticleHighlights] (TL;DR, gạch đầu dòng ý chính, thời gian đọc
     * tiết kiệm, topic tags). Tự động fallback sang phân tích ngoại tuyến nếu Gemini không khả dụng.
     */
    suspend fun extractHighlights(
        title: String,
        plainText: String,
        languageTag: String = currentLanguageTag(),
    ): ArticleHighlights = withContext(Dispatchers.IO) {
        val cleaned = plainText.trim()
        Log.d(TAG, "[extractHighlights] start title=\"${title.take(60)}\" plainTextLen=${cleaned.length} lang=$languageTag")
        if (cleaned.isBlank()) {
            Log.w(TAG, "[extractHighlights] ❌ EmptyContent")
            throw SummaryException.EmptyContent
        }

        val totalWords = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
        val keys = GeminiConfig.API_KEYS.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        if (keys.isEmpty()) {
            Log.w(TAG, "[extractHighlights] GeminiConfig.API_KEYS rỗng -> fallback sang offline heuristics")
            return@withContext ArticleHighlightsExtractor.extractOfflineHighlights(title, cleaned)
        }

        val body = cleaned.take(MAX_INPUT_CHARS)
        val requestBody = buildHighlightsRequestBody(title, body, languageTag)

        var lastError: SummaryException? = null
        for ((index, key) in keys.withIndex()) {
            Log.d(TAG, "[extractHighlights] thử key #${index + 1}/${keys.size} (${mask(key)})")
            try {
                val rawText = callGeminiRaw(key, requestBody)
                val highlights = ArticleHighlightsExtractor.parseGeminiResponse(rawText, totalWords)
                Log.d(TAG, "[extractHighlights] ✅ key #${index + 1} OK, takeaways=${highlights.keyTakeaways.size} timeSaved=${highlights.readingTimeSavedMin}m")
                return@withContext highlights
            } catch (e: SummaryException) {
                lastError = e
                val tryNext = e is SummaryException.InvalidApiKey ||
                    e is SummaryException.RateLimited ||
                    e is SummaryException.Http
                Log.w(TAG, "[extractHighlights] key #${index + 1} lỗi: ${e::class.simpleName}, tryNext=$tryNext")
                if (!tryNext) break
            } catch (e: Exception) {
                Log.w(TAG, "[extractHighlights] unexpected error: $e")
                break
            }
        }

        Log.w(TAG, "[extractHighlights] Không gọi được Gemini ($lastError), kích hoạt fallback ngoại tuyến")
        ArticleHighlightsExtractor.extractOfflineHighlights(title, cleaned)
    }

    /**
     * Trích xuất sơ đồ tư duy dạng cây phân cấp [ArticleMindMap].
     * Tự động fallback sang offline heuristics nếu Gemini không khả dụng.
     */
    suspend fun generateMindMap(
        title: String,
        plainText: String,
        languageTag: String = currentLanguageTag(),
    ): ArticleMindMap = withContext(Dispatchers.IO) {
        val cleaned = plainText.trim()
        Log.d(TAG, "[generateMindMap] start title=\"${title.take(60)}\" plainTextLen=${cleaned.length} lang=$languageTag")
        if (cleaned.isBlank()) {
            Log.w(TAG, "[generateMindMap] ❌ EmptyContent")
            throw SummaryException.EmptyContent
        }

        val keys = GeminiConfig.API_KEYS.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) {
            Log.w(TAG, "[generateMindMap] GeminiConfig.API_KEYS rỗng -> fallback sang offline mindmap")
            return@withContext ArticleMindMapExtractor.extractOfflineMindMap(title, cleaned)
        }

        val body = cleaned.take(MAX_INPUT_CHARS)
        val requestBody = buildMindMapRequestBody(title, body, languageTag)

        var lastError: SummaryException? = null
        for ((index, key) in keys.withIndex()) {
            Log.d(TAG, "[generateMindMap] thử key #${index + 1}/${keys.size} (${mask(key)})")
            try {
                val rawText = callGeminiRaw(key, requestBody)
                val mindMap = ArticleMindMapExtractor.parseGeminiResponse(rawText, title)
                Log.d(TAG, "[generateMindMap] ✅ key #${index + 1} OK, nodes=${mindMap.nodes.size}")
                return@withContext mindMap
            } catch (e: SummaryException) {
                lastError = e
                val tryNext = e is SummaryException.InvalidApiKey ||
                    e is SummaryException.RateLimited ||
                    e is SummaryException.Http
                Log.w(TAG, "[generateMindMap] key #${index + 1} lỗi: ${e::class.simpleName}, tryNext=$tryNext")
                if (!tryNext) break
            } catch (e: Exception) {
                Log.w(TAG, "[generateMindMap] unexpected error: $e")
                break
            }
        }

        Log.w(TAG, "[generateMindMap] Không gọi được Gemini ($lastError), kích hoạt fallback ngoại tuyến")
        ArticleMindMapExtractor.extractOfflineMindMap(title, cleaned)
    }

    /**
     * Gửi câu hỏi tương tác về bài báo đến Gemini (AI Deep Read).
     * Bám sát ngữ cảnh bài viết (grounded), tự động fallback sang offline engine nếu Gemini không khả dụng.
     */
    suspend fun askArticleQuestion(
        title: String,
        plainText: String,
        chatHistory: List<DeepReadMessage>,
        question: String,
        languageTag: String = currentLanguageTag(),
    ): DeepReadMessage = withContext(Dispatchers.IO) {
        val cleaned = plainText.trim()
        Log.d(TAG, "[askArticleQuestion] start title=\"${title.take(50)}\" q=\"${question.take(50)}\" lang=$languageTag")
        if (cleaned.isBlank()) {
            return@withContext ArticleDeepReadEngine.generateOfflineAnswer(title, "", question, languageTag)
        }

        val keys = GeminiConfig.API_KEYS.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) {
            Log.w(TAG, "[askArticleQuestion] GeminiConfig.API_KEYS rỗng -> fallback offline")
            return@withContext ArticleDeepReadEngine.generateOfflineAnswer(title, cleaned, question, languageTag)
        }

        val body = cleaned.take(MAX_INPUT_CHARS)
        val requestBody = buildDeepReadRequestBody(title, body, chatHistory, question, languageTag)

        var lastError: SummaryException? = null
        for ((index, key) in keys.withIndex()) {
            Log.d(TAG, "[askArticleQuestion] thử key #${index + 1}/${keys.size} (${mask(key)})")
            try {
                val rawAnswer = callGeminiRaw(key, requestBody)
                Log.d(TAG, "[askArticleQuestion] ✅ key #${index + 1} OK, answerLen=${rawAnswer.length}")
                return@withContext DeepReadMessage(
                    sender = DeepReadSender.ASSISTANT,
                    content = rawAnswer.trim(),
                    isOfflineFallback = false,
                    isGrounded = true,
                )
            } catch (e: SummaryException) {
                lastError = e
                val tryNext = e is SummaryException.InvalidApiKey ||
                    e is SummaryException.RateLimited ||
                    e is SummaryException.Http
                Log.w(TAG, "[askArticleQuestion] key #${index + 1} lỗi: ${e::class.simpleName}, tryNext=$tryNext")
                if (!tryNext) break
            } catch (e: Exception) {
                Log.w(TAG, "[askArticleQuestion] unexpected error: $e")
                break
            }
        }

        Log.w(TAG, "[askArticleQuestion] Không gọi được Gemini ($lastError), kích hoạt fallback ngoại tuyến")
        ArticleDeepReadEngine.generateOfflineAnswer(title, cleaned, question, languageTag)
    }

    /**
     * Tóm tắt [plainText] thành chuỗi văn bản thuần (giữ tương thích ngược).
     */
    suspend fun summarize(
        title: String,
        plainText: String,
        languageTag: String = currentLanguageTag(),
    ): String = extractHighlights(title, plainText, languageTag).formatAsPlainText()

    /** Gọi Gemini với 1 key cụ thể, trả về văn bản trích xuất từ candidate 0 hoặc ném [SummaryException]. */
    private fun callGeminiRaw(apiKey: String, requestBody: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${GeminiConfig.MODEL}:generateContent?key=$apiKey"
        Log.d(TAG, "[callGeminiRaw] POST .../models/${GeminiConfig.MODEL}:generateContent?key=${mask(apiKey)}")

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            Log.w(TAG, "[callGeminiRaw] ❌ Network error: ${e.message}")
            throw SummaryException.Network
        }
        return response.use {
            val responseBody = it.body?.string().orEmpty()
            Log.d(TAG, "[callGeminiRaw] HTTP ${it.code} responseLen=${responseBody.length}")
            if (!it.isSuccessful) {
                Log.w(TAG, "[callGeminiRaw] ❌ API error body=${responseBody.take(300)}")
                throw mapHttpError(it.code)
            }
            parseSummary(responseBody)
        }
    }

    /** Gọi Gemini trả về văn bản tóm tắt hoặc ném [SummaryException]. */
    private fun callGemini(apiKey: String, requestBody: String): String = callGeminiRaw(apiKey, requestBody)

    private fun buildHighlightsRequestBody(title: String, body: String, languageTag: String): String {
        val prompt = buildString {
            append("Analyze the article below and return a concise, high-value highlights summary.\n")
            append("Provide the response strictly as a JSON object with this structure:\n")
            append("{\n")
            append("  \"tldr\": \"1-2 sentence executive overview\",\n")
            append("  \"takeaways\": [\"3 to 5 clear, concise key takeaway bullet points (do not include bullet symbols)\"],\n")
            append("  \"tags\": [\"2 to 4 key topic keywords/tags\"]\n")
            append("}\n")
            append("Return ONLY raw JSON, with no markdown formatting code blocks (no ```json) and no intro/outro.\n")
            append("Write all text in the language corresponding to BCP-47 tag \"$languageTag\".\n\n")
            if (title.isNotBlank()) append("Title: $title\n\n")
            append("Content:\n")
            append(body)
        }
        return JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().put("temperature", 0.2))
        }.toString()
    }

    private fun buildMindMapRequestBody(title: String, body: String, languageTag: String): String {
        val prompt = buildString {
            append("Analyze the article below and construct a hierarchical concept mind map in JSON format.\n")
            append("Respond strictly with a JSON object conforming to this schema:\n")
            append("{\n")
            append("  \"root\": \"Core Subject (3-6 words)\",\n")
            append("  \"nodes\": [\n")
            append("    {\n")
            append("      \"id\": \"root\",\n")
            append("      \"label\": \"Core Subject\",\n")
            append("      \"detail\": \"1-2 sentence core thesis\",\n")
            append("      \"depth\": 0,\n")
            append("      \"parentId\": null,\n")
            append("      \"tag\": \"Central Theme\"\n")
            append("    },\n")
            append("    {\n")
            append("      \"id\": \"branch_1\",\n")
            append("      \"label\": \"Main Branch / Pillar (3-5 words)\",\n")
            append("      \"detail\": \"Explanation of this aspect\",\n")
            append("      \"depth\": 1,\n")
            append("      \"parentId\": \"root\",\n")
            append("      \"tag\": \"Context\"\n")
            append("    },\n")
            append("    {\n")
            append("      \"id\": \"sub_1_1\",\n")
            append("      \"label\": \"Key Detail / Supporting Fact\",\n")
            append("      \"detail\": \"Specific fact, figure or quote\",\n")
            append("      \"depth\": 2,\n")
            append("      \"parentId\": \"branch_1\"\n")
            append("    }\n")
            append("  ]\n")
            append("}\n")
            append("Rules:\n")
            append("- Provide 1 root node (depth 0, parentId null)\n")
            append("- Provide 2 to 4 main branches (depth 1, parentId 'root')\n")
            append("- Provide 1 to 2 supporting sub-nodes per branch (depth 2)\n")
            append("- Keep labels concise (under 6 words). Put rich explanation in 'detail'.\n")
            append("- Return ONLY valid raw JSON with NO markdown code fences (no ```json).\n")
            append("- Write all text in the language corresponding to BCP-47 tag \"$languageTag\".\n\n")
            if (title.isNotBlank()) append("Title: $title\n\n")
            append("Content:\n")
            append(body)
        }
        return JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().put("temperature", 0.2))
        }.toString()
    }

    private fun buildDeepReadRequestBody(
        title: String,
        body: String,
        chatHistory: List<DeepReadMessage>,
        question: String,
        languageTag: String,
    ): String {
        val prompt = buildString {
            append("You are an expert AI reading assistant for an article.\n")
            append("Answer the user's question accurately, concisely, and directly based SOLELY on the provided article context.\n")
            append("Rules:\n")
            append("- Ground your answers strictly on the facts, evidence, arguments, and data present in the article.\n")
            append("- If the question cannot be answered from the article, state clearly that the article does not mention this.\n")
            append("- Do not hallucinate or extrapolate external claims without citing that it is outside the text.\n")
            append("- Keep explanations punchy, well-structured (use bullet points where appropriate), and easy to read on mobile.\n")
            append("- Write the response strictly in the language with BCP-47 tag \"$languageTag\".\n\n")
            if (title.isNotBlank()) append("Article Title: $title\n\n")
            append("Article Context:\n$body\n\n")
            if (chatHistory.isNotEmpty()) {
                append("Previous Conversation Turns:\n")
                chatHistory.takeLast(4).forEach { msg ->
                    val role = if (msg.sender == DeepReadSender.USER) "User" else "Assistant"
                    append("$role: ${msg.content.trim()}\n")
                }
                append("\n")
            }
            append("Current User Question: $question\n")
            append("Direct Answer:")
        }
        return JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }.toString()
    }

    private fun buildRequestBody(title: String, body: String, languageTag: String): String {
        // Prompt giữ tiếng Anh (trung lập); kết quả được yêu cầu trả theo [languageTag] của app.
        val prompt = buildString {
            append("Summarize the article below into 3-5 short bullet points, ")
            append("each line starting with \"- \". ")
            append("Return only the bullet points, with no preamble or conclusion. ")
            append("Write the answer in the language with BCP-47 tag \"$languageTag\".\n\n")
            if (title.isNotBlank()) append("Title: $title\n\n")
            append("Content:\n")
            append(body)
        }
        return JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }.toString()
    }

    private fun parseSummary(json: String): String {
        return try {
            val text = JSONObject(json)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
            text.ifBlank { throw SummaryException.EmptyResponse }
        } catch (e: SummaryException) {
            throw e
        } catch (e: Exception) {
            throw SummaryException.ParseError
        }
    }

    private fun mapHttpError(code: Int): SummaryException = when (code) {
        400, 403 -> SummaryException.InvalidApiKey
        429 -> SummaryException.RateLimited
        else -> SummaryException.Http(code)
    }

    /**
     * Ngôn ngữ hiện tại của app (theo lựa chọn trong Settings, đã được RApp wrap vào context),
     * dùng để yêu cầu Gemini trả tóm tắt ĐÚNG ngôn ngữ người dùng đang xem.
     */
    private fun currentLanguageTag(): String {
        val locales = context.resources.configuration.locales
        val locale = if (!locales.isEmpty) locales[0] else Locale.getDefault()
        return locale.toLanguageTag()
    }

    /** Che bớt key khi log để không lộ key thật. */
    private fun mask(key: String): String = when {
        key.isBlank() -> "<empty>"
        key.length <= 8 -> "***"
        else -> "${key.take(4)}…${key.takeLast(4)} (len=${key.length})"
    }

    companion object {
        private const val TAG = "roy93~AI"
        private const val MAX_INPUT_CHARS = 12_000
    }
}
