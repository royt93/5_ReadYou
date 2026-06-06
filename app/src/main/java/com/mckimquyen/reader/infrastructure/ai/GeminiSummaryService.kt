package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import android.util.Log
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
 * Gọi Google Gemini REST API để tóm tắt nội dung bài báo (quick win "AI TL;DR").
 *
 * Key lấy từ [GeminiConfig.API_KEYS] (class constant). Hỗ trợ FAILOVER: thử lần lượt từng key,
 * nếu một key lỗi (sai key 400/403 hoặc hết quota 429, hoặc server lỗi) thì tự chuyển sang key
 * kế tiếp trong danh sách. Hết key vẫn lỗi -> ném lỗi cuối cùng cho UI hiển thị.
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
     * Tóm tắt [plainText] thành các gạch đầu dòng. Chạy trên [Dispatchers.IO].
     * Thử lần lượt các key trong [GeminiConfig.API_KEYS]; key lỗi -> dùng key kế tiếp.
     */
    suspend fun summarize(
        title: String,
        plainText: String,
        languageTag: String = currentLanguageTag(),
    ): String = withContext(Dispatchers.IO) {
        val keys = GeminiConfig.API_KEYS.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        Log.d(TAG, "[summarize] start title=\"${title.take(60)}\" plainTextLen=${plainText.length} lang=$languageTag keys=${keys.size}")
        if (keys.isEmpty()) {
            Log.w(TAG, "[summarize] ❌ MissingApiKey (GeminiConfig.API_KEYS rỗng)")
            throw SummaryException.MissingApiKey
        }

        val cleaned = plainText.trim()
        if (cleaned.isBlank()) {
            Log.w(TAG, "[summarize] ❌ EmptyContent")
            throw SummaryException.EmptyContent
        }

        // Cắt bớt để tránh vượt giới hạn token & tiết kiệm quota.
        val body = cleaned.take(MAX_INPUT_CHARS)
        val requestBody = buildRequestBody(title, body, languageTag)
        Log.d(TAG, "[summarize] sending bodyLen=${body.length} (truncated=${cleaned.length > MAX_INPUT_CHARS})")

        var lastError: SummaryException = SummaryException.MissingApiKey
        for ((index, key) in keys.withIndex()) {
            Log.d(TAG, "[summarize] thử key #${index + 1}/${keys.size} (${mask(key)})")
            try {
                val summary = callGemini(key, requestBody)
                Log.d(TAG, "[summarize] ✅ key #${index + 1} OK, summaryLen=${summary.length}\n$summary")
                return@withContext summary
            } catch (e: SummaryException) {
                lastError = e
                // Chỉ chuyển key khi lỗi liên quan key/quota/server; lỗi khác (mạng, parse) thì dừng.
                val tryNext = e is SummaryException.InvalidApiKey ||
                    e is SummaryException.RateLimited ||
                    e is SummaryException.Http
                Log.w(TAG, "[summarize] key #${index + 1} lỗi: ${e::class.simpleName}, tryNext=$tryNext")
                if (!tryNext) throw e
            }
        }
        Log.w(TAG, "[summarize] ❌ Hết key, lỗi cuối: ${lastError::class.simpleName}")
        throw lastError
    }

    /** Gọi Gemini với 1 key cụ thể. Trả về summary hoặc ném [SummaryException]. */
    private fun callGemini(apiKey: String, requestBody: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${GeminiConfig.MODEL}:generateContent?key=$apiKey"
        Log.d(TAG, "[callGemini] POST .../models/${GeminiConfig.MODEL}:generateContent?key=${mask(apiKey)}")

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            Log.w(TAG, "[callGemini] ❌ Network error: ${e.message}")
            throw SummaryException.Network
        }
        return response.use {
            val responseBody = it.body?.string().orEmpty()
            Log.d(TAG, "[callGemini] HTTP ${it.code} responseLen=${responseBody.length}")
            if (!it.isSuccessful) {
                Log.w(TAG, "[callGemini] ❌ API error body=${responseBody.take(300)}")
                throw mapHttpError(it.code)
            }
            parseSummary(responseBody)
        }
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
