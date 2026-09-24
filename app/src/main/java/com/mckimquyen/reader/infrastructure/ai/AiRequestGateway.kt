package com.mckimquyen.reader.infrastructure.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.google.gson.JsonParser
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gateway thống nhất chịu trách nhiệm gọi Google Gemini REST API.
 *
 * Tính năng chính:
 * 1. Tập trung xử lý key rotation / failover (thử lần lượt từng key nếu gặp 400, 403, 429, lỗi HTTP).
 * 2. Cấu hình timeout riêng (60s callTimeout / readTimeout / writeTimeout) độc lập với OkHttpClient mặc định của app.
 * 3. Hỗ trợ Coroutine Cancellation: hủy HTTP request ngầm ngay khi coroutine cha bị cancel qua [Call.cancel].
 * 4. Single-flight: các request đồng thời có cùng fingerprint (useCase + model + requestBody SHA-256)
 *    sẽ chia sẻ chung 1 network call, tránh lãng phí quota API khi user bấm liên tiếp.
 *    Hỗ trợ waiter counting an toàn để khi 1 caller bị hủy thì các caller khác vẫn nhận được kết quả.
 */
@Singleton
class AiRequestGateway internal constructor(
    private val aiHttpClient: OkHttpClient,
    private val externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    forTesting: Boolean,
) {

    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        @ApplicationScope externalScope: CoroutineScope,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        aiHttpClient = okHttpClient.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build(),
        externalScope = externalScope,
        ioDispatcher = ioDispatcher,
        forTesting = false,
    )

    private class InFlightEntry(
        val deferred: Deferred<String>,
        val cancelCall: () -> Unit,
        val waiterCount: AtomicInteger = AtomicInteger(1),
    )

    private val inFlight = ConcurrentHashMap<String, InFlightEntry>()
    private val flightMutex = Mutex()

    /**
     * Thực thi một request Gemini với cơ chế Single-Flight và Key Failover.
     *
     * @param useCase Tên định danh use case (vd: "highlights", "mindmap", "deepread")
     * @param requestBody JSON request body gửi tới Gemini
     * @param keys Danh sách API key đã giải mã
     * @return Chuỗi nội dung text trích xuất từ candidate 0 của Gemini
     */
    suspend fun execute(
        useCase: String,
        requestBody: String,
        keys: List<String> = GeminiConfig.API_KEYS,
    ): String {
        val distinctKeys = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinctKeys.isEmpty()) {
            throw GeminiSummaryService.SummaryException.MissingApiKey
        }

        val requestFingerprint = computeFingerprint(useCase, GeminiConfig.MODEL, requestBody)

        val entry = flightMutex.withLock {
            val existing = inFlight[requestFingerprint]
            if (existing != null) {
                existing.waiterCount.incrementAndGet()
                Log.d(TAG, "[$useCase] Single-flight hit for fp=${requestFingerprint.take(8)} (waiters=${existing.waiterCount.get()})")
                existing
            } else {
                var activeCall: Call? = null
                val deferred = CompletableDeferred<String>()
                val newEntry = InFlightEntry(
                    deferred = deferred,
                    cancelCall = { activeCall?.cancel() },
                )
                inFlight[requestFingerprint] = newEntry

                // Chạy network execution ngầm trên externalScope
                externalScope.launch(ioDispatcher) {
                    try {
                        val result = executeWithFailover(
                            useCase = useCase,
                            requestBody = requestBody,
                            keys = distinctKeys,
                            onCallCreated = { call -> activeCall = call }
                        )
                        deferred.complete(result)
                    } catch (ce: CancellationException) {
                        deferred.cancel(ce)
                    } catch (t: Throwable) {
                        deferred.completeExceptionally(t)
                    } finally {
                        flightMutex.withLock {
                            inFlight.remove(requestFingerprint, newEntry)
                        }
                    }
                }
                newEntry
            }
        }

        return try {
            entry.deferred.await()
        } catch (e: CancellationException) {
            val remaining = entry.waiterCount.decrementAndGet()
            Log.d(TAG, "[$useCase] Caller cancelled for fp=${requestFingerprint.take(8)}, remaining waiters=$remaining")
            if (remaining <= 0) {
                Log.d(TAG, "[$useCase] No more waiters for fp=${requestFingerprint.take(8)}, cancelling in-flight call")
                entry.cancelCall()
                flightMutex.withLock {
                    inFlight.remove(requestFingerprint, entry)
                }
            }
            throw e
        }
    }

    /**
     * Thử lần lượt từng API key trong [keys].
     */
    private suspend fun executeWithFailover(
        useCase: String,
        requestBody: String,
        keys: List<String>,
        onCallCreated: (Call) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        var lastError: GeminiSummaryService.SummaryException? = null

        for ((index, key) in keys.withIndex()) {
            Log.d(TAG, "[$useCase] Thử key #${index + 1}/${keys.size} (${mask(key)})")
            try {
                val rawAnswer = callGeminiCancellable(key, requestBody, onCallCreated)
                Log.d(TAG, "[$useCase] ✅ key #${index + 1} thành công (len=${rawAnswer.length})")
                return@withContext rawAnswer
            } catch (e: GeminiSummaryService.SummaryException) {
                lastError = e
                val tryNext = shouldFailover(e)
                Log.w(TAG, "[$useCase] key #${index + 1} lỗi: ${e::class.simpleName}, tryNext=$tryNext")
                if (!tryNext) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[$useCase] Lỗi không lường trước: $e")
                break
            }
        }

        throw lastError ?: GeminiSummaryService.SummaryException.Network
    }

    /**
     * Gọi Gemini REST API bất đồng bộ có hỗ trợ Cancellation.
     */
    private suspend fun callGeminiCancellable(
        apiKey: String,
        requestBody: String,
        onCallCreated: (Call) -> Unit,
    ): String = suspendCancellableCoroutine { continuation ->
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${GeminiConfig.MODEL}:generateContent?key=$apiKey"
        Log.d(TAG, "[callGeminiCancellable] POST .../models/${GeminiConfig.MODEL}:generateContent?key=${mask(apiKey)}")

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val call = aiHttpClient.newCall(request)
        onCallCreated(call)

        continuation.invokeOnCancellation {
            Log.d(TAG, "[callGeminiCancellable] Coroutine cancelled -> cancelling OkHttp Call")
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) {
                    return
                }
                Log.w(TAG, "[callGeminiCancellable] ❌ Network error: ${e.message}")
                continuation.resumeWithException(GeminiSummaryService.SummaryException.Network)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) {
                    response.close()
                    return
                }

                response.use { res ->
                    val responseBody = res.body?.string().orEmpty()
                    Log.d(TAG, "[callGeminiCancellable] HTTP ${res.code} responseLen=${responseBody.length}")
                    if (!res.isSuccessful) {
                        Log.w(TAG, "[callGeminiCancellable] ❌ API error body=${responseBody.take(300)}")
                        val ex = mapHttpError(res.code)
                        continuation.resumeWithException(ex)
                        return
                    }

                    try {
                        val parsed = parseSummaryText(responseBody)
                        continuation.resume(parsed)
                    } catch (e: Throwable) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        })
    }

    private fun shouldFailover(e: GeminiSummaryService.SummaryException): Boolean {
        return e is GeminiSummaryService.SummaryException.InvalidApiKey ||
            e is GeminiSummaryService.SummaryException.RateLimited ||
            e is GeminiSummaryService.SummaryException.Http
    }

    private fun mapHttpError(code: Int): GeminiSummaryService.SummaryException = when (code) {
        400, 403 -> GeminiSummaryService.SummaryException.InvalidApiKey
        429 -> GeminiSummaryService.SummaryException.RateLimited
        else -> GeminiSummaryService.SummaryException.Http(code)
    }

    private fun parseSummaryText(json: String): String {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val text = root
                .getAsJsonArray("candidates")
                .get(0).asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).asJsonObject
                .get("text").asString
                .trim()
            text.ifBlank { throw GeminiSummaryService.SummaryException.EmptyResponse }
        } catch (e: GeminiSummaryService.SummaryException) {
            throw e
        } catch (e: Exception) {
            throw GeminiSummaryService.SummaryException.ParseError
        }
    }

    private fun computeFingerprint(useCase: String, model: String, body: String): String {
        val input = "$useCase:$model:$body"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun mask(key: String): String = when {
        key.isBlank() -> "<empty>"
        key.length <= 8 -> "***"
        else -> "${key.take(4)}…${key.takeLast(4)} (len=${key.length})"
    }

    companion object {
        private const val TAG = "roy93~AiGateway"
    }
}
