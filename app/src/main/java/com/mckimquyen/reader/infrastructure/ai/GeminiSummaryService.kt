package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.get
import com.mckimquyen.reader.ui.ext.put
import com.mckimquyen.reader.ui.ext.dataStore
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
 * Key được giải quyết theo thứ tự ưu tiên:
 * 1. Key do người dùng tự nhập trong app ([DataStoreKeys.GeminiApiKey]).
 * 2. Key dev nhúng sẵn ([BuildConfig.GEMINI_API_KEY]).
 *
 * Nếu cả hai đều trống, ném [SummaryException.MissingApiKey] để UI hiển thị ô nhập key.
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

    /** Trả về true nếu đã có key (của user hoặc dev), dùng để UI quyết định hiển thị ô nhập key. */
    fun hasApiKey(): Boolean {
        val has = resolveApiKey().isNotBlank()
        Log.d(TAG, "[hasApiKey] => $has")
        return has
    }

    /** Lưu key do người dùng nhập vào DataStore (không nằm trong APK). */
    suspend fun saveUserApiKey(key: String) {
        Log.d(TAG, "[saveUserApiKey] saving user key (${mask(key.trim())})")
        context.dataStore.put(DataStoreKeys.GeminiApiKey, key.trim())
        Log.d(TAG, "[saveUserApiKey] ✅ saved")
    }

    private fun resolveApiKey(): String {
        val userKey = context.dataStore.get(DataStoreKeys.GeminiApiKey).orEmpty().trim()
        val devKey = BuildConfig.GEMINI_API_KEY.trim()
        val source = when {
            userKey.isNotBlank() -> "USER"
            devKey.isNotBlank() -> "DEV(BuildConfig)"
            else -> "NONE"
        }
        val resolved = userKey.ifBlank { devKey }
        Log.d(TAG, "[resolveApiKey] source=$source key=${mask(resolved)}")
        return resolved
    }

    /** Che bớt key khi log để không lộ key thật. */
    private fun mask(key: String): String = when {
        key.isBlank() -> "<empty>"
        key.length <= 8 -> "***"
        else -> "${key.take(4)}…${key.takeLast(4)} (len=${key.length})"
    }

    /**
     * Tóm tắt [plainText] thành các gạch đầu dòng. Chạy trên [Dispatchers.IO].
     * @throws SummaryException khi thiếu key, thiếu nội dung, hoặc lỗi mạng/API.
     */
    suspend fun summarize(
        title: String,
        plainText: String,
        languageTag: String = Locale.getDefault().toLanguageTag(),
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "[summarize] start title=\"${title.take(60)}\" plainTextLen=${plainText.length} lang=$languageTag")
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "[summarize] ❌ MissingApiKey")
            throw SummaryException.MissingApiKey
        }

        val cleaned = plainText.trim()
        if (cleaned.isBlank()) {
            Log.w(TAG, "[summarize] ❌ EmptyContent")
            throw SummaryException.EmptyContent
        }

        // Cắt bớt để tránh vượt giới hạn token & tiết kiệm quota free tier.
        val body = cleaned.take(MAX_INPUT_CHARS)
        Log.d(TAG, "[summarize] sending bodyLen=${body.length} (truncated=${cleaned.length > MAX_INPUT_CHARS})")

        // Prompt giữ tiếng Anh (trung lập, không phụ thuộc locale); kết quả vẫn được yêu cầu
        // trả về theo ngôn ngữ BCP-47 của thiết bị qua [languageTag].
        val prompt = buildString {
            append("Summarize the article below into 3-5 short bullet points, ")
            append("each line starting with \"- \". ")
            append("Return only the bullet points, with no preamble or conclusion. ")
            append("Write the answer in the language with BCP-47 tag \"$languageTag\".\n\n")
            if (title.isNotBlank()) append("Title: $title\n\n")
            append("Content:\n")
            append(body)
        }

        val requestJson = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "$MODEL:generateContent?key=$apiKey"
        // Log URL nhưng giấu key.
        Log.d(TAG, "[summarize] POST .../models/$MODEL:generateContent?key=${mask(apiKey)}")

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            Log.w(TAG, "[summarize] ❌ Network error: ${e.message}")
            throw SummaryException.Network
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            Log.d(TAG, "[summarize] HTTP ${it.code} responseLen=${responseBody.length}")
            if (!it.isSuccessful) {
                Log.w(TAG, "[summarize] ❌ API error body=${responseBody.take(500)}")
                throw mapHttpError(it.code)
            }
            val summary = parseSummary(responseBody)
            Log.d(TAG, "[summarize] ✅ summaryLen=${summary.length}\n$summary")
            summary
        }
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

    companion object {
        private const val TAG = "roy93~AI"
        private const val MODEL = "gemini-1.5-flash-latest"
        private const val MAX_INPUT_CHARS = 12_000
    }
}
