package com.mckimquyen.reader.infrastructure.rss.scraper

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface WebFeedDiscoveryResult {
    data class Preview(val siteTitle: String, val articles: List<WebArticleCandidate>) : WebFeedDiscoveryResult
    data object InvalidUrl : WebFeedDiscoveryResult
    data object UnsafeUrl : WebFeedDiscoveryResult
    data object RedirectRejected : WebFeedDiscoveryResult
    data object NonHtml : WebFeedDiscoveryResult
    data class HttpError(val statusCode: Int) : WebFeedDiscoveryResult
    data object Timeout : WebFeedDiscoveryResult
    data object ResponseTooLarge : WebFeedDiscoveryResult
    data object Unsupported : WebFeedDiscoveryResult
    data object Ambiguous : WebFeedDiscoveryResult
    data object TooFewCandidates : WebFeedDiscoveryResult
}

sealed interface WebFeedRefreshResult {
    data class Success(val articles: List<WebArticleCandidate>) : WebFeedRefreshResult
    data class Failure(val reason: WebFeedDiscoveryResult) : WebFeedRefreshResult
}

class WebFeedScraper @Inject constructor(
    private val httpClient: WebFeedHttpClient,
    private val parser: WebFeedParser,
) {
    internal constructor(httpClient: WebFeedHttpClient, parser: WebFeedParser, maxResponseBytes: Long) : this(httpClient, parser) {
        this.responseByteLimit = maxResponseBytes
    }

    private var responseByteLimit: Long = DEFAULT_MAX_RESPONSE_BYTES
    suspend fun discover(url: String): WebFeedDiscoveryResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
            ?: return@withContext WebFeedDiscoveryResult.InvalidUrl
        val requestClient = try {
            httpClient.create(httpUrl.toString())
        } catch (_: java.net.UnknownHostException) {
            return@withContext WebFeedDiscoveryResult.UnsafeUrl
        } catch (_: IllegalArgumentException) {
            return@withContext WebFeedDiscoveryResult.UnsafeUrl
        } catch (_: java.io.IOException) {
            return@withContext WebFeedDiscoveryResult.UnsafeUrl
        }
        fetchAndParse(httpUrl.toString(), requestClient)
    }

    suspend fun refresh(sourceUrl: String): WebFeedRefreshResult = when (val result = discover(sourceUrl)) {
        is WebFeedDiscoveryResult.Preview -> WebFeedRefreshResult.Success(result.articles)
        else -> WebFeedRefreshResult.Failure(result)
    }

    private suspend fun fetchAndParse(url: String, requestClient: OkHttpClient): WebFeedDiscoveryResult =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = requestClient.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    continuation.resumeWith(Result.success(networkFailure(e)))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val result = runCatching {
                        response.use { parseResponse(url, it) }
                    }.recoverCatching { e ->
                        if (e is java.io.IOException) networkFailure(e) else throw e
                    }
                    continuation.resumeWith(result)
                }
            })
        }

    private fun networkFailure(error: java.io.IOException): WebFeedDiscoveryResult =
        if (error is java.net.SocketTimeoutException) WebFeedDiscoveryResult.Timeout
        else WebFeedDiscoveryResult.HttpError(0)

    private fun parseResponse(url: String, safeResponse: okhttp3.Response): WebFeedDiscoveryResult {
            if (safeResponse.code in REDIRECT_STATUS_CODES) return WebFeedDiscoveryResult.RedirectRejected
            if (!safeResponse.isSuccessful) return WebFeedDiscoveryResult.HttpError(safeResponse.code)
            val contentType = safeResponse.header(CONTENT_TYPE_HEADER).orEmpty()
            if (!contentType.substringBefore(';').trim().equals(HTML_CONTENT_TYPE, ignoreCase = true)) {
                return WebFeedDiscoveryResult.NonHtml
            }
            val body = safeResponse.body ?: return WebFeedDiscoveryResult.NonHtml
            if (body.contentLength() > responseByteLimit) return WebFeedDiscoveryResult.ResponseTooLarge
            val source = body.source()
            val buffer = okio.Buffer()
            while (buffer.size <= responseByteLimit) {
                val remaining = responseByteLimit + 1 - buffer.size
                val read = source.read(buffer, minOf(remaining, READ_CHUNK_BYTES))
                if (read == -1L) break
            }
            if (buffer.size > responseByteLimit) return WebFeedDiscoveryResult.ResponseTooLarge
            val html = buffer.readByteArray().toString(Charsets.UTF_8)
            return when (val parsed = parser.parse(url.toHttpUrlOrNull() ?: return WebFeedDiscoveryResult.InvalidUrl, html)) {
                is WebFeedParseResult.Success -> WebFeedDiscoveryResult.Preview(parsed.siteTitle, parsed.articles)
                WebFeedParseResult.Unsupported -> WebFeedDiscoveryResult.Unsupported
                WebFeedParseResult.Ambiguous -> WebFeedDiscoveryResult.Ambiguous
                WebFeedParseResult.TooFewCandidates -> WebFeedDiscoveryResult.TooFewCandidates
            }
    }

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val HTML_CONTENT_TYPE = "text/html"
        const val READ_CHUNK_BYTES = 8_192L
        val REDIRECT_STATUS_CODES = 300..399
    }
}
