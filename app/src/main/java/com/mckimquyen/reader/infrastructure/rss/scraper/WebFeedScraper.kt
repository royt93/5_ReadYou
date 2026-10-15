package com.mckimquyen.reader.infrastructure.rss.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

internal sealed interface WebFeedDiscoveryResult {
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

internal sealed interface WebFeedRefreshResult {
    data class Success(val articles: List<WebArticleCandidate>) : WebFeedRefreshResult
    data class Failure(val reason: WebFeedDiscoveryResult) : WebFeedRefreshResult
}

internal class WebFeedScraper(
    private val clientFactory: (String) -> OkHttpClient,
    private val parser: WebFeedParser,
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
) {
    suspend fun discover(url: String): WebFeedDiscoveryResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
            ?: return@withContext WebFeedDiscoveryResult.InvalidUrl
        val requestClient = try {
            clientFactory(httpUrl.toString())
        } catch (_: IllegalArgumentException) {
            return@withContext WebFeedDiscoveryResult.UnsafeUrl
        } catch (_: java.net.UnknownHostException) {
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

    private suspend fun fetchAndParse(url: String, requestClient: OkHttpClient): WebFeedDiscoveryResult {
        val request = Request.Builder().url(url).get().build()
        val response = try {
            requestClient.newCall(request).execute()
        } catch (_: java.net.SocketTimeoutException) {
            return WebFeedDiscoveryResult.Timeout
        } catch (_: java.io.IOException) {
            return WebFeedDiscoveryResult.HttpError(0)
        }
        response.use { safeResponse ->
            if (safeResponse.code in REDIRECT_STATUS_CODES) return WebFeedDiscoveryResult.RedirectRejected
            if (!safeResponse.isSuccessful) return WebFeedDiscoveryResult.HttpError(safeResponse.code)
            val contentType = safeResponse.header(CONTENT_TYPE_HEADER).orEmpty()
            if (!contentType.substringBefore(';').trim().equals(HTML_CONTENT_TYPE, ignoreCase = true)) {
                return WebFeedDiscoveryResult.NonHtml
            }
            val body = safeResponse.body ?: return WebFeedDiscoveryResult.NonHtml
            if (body.contentLength() > maxResponseBytes) return WebFeedDiscoveryResult.ResponseTooLarge
            val source = body.source()
            val buffer = okio.Buffer()
            while (buffer.size <= maxResponseBytes) {
                val remaining = maxResponseBytes + 1 - buffer.size
                val read = source.read(buffer, minOf(remaining, READ_CHUNK_BYTES))
                if (read == -1L) break
            }
            if (buffer.size > maxResponseBytes) return WebFeedDiscoveryResult.ResponseTooLarge
            val html = buffer.readByteArray().toString(Charsets.UTF_8)
            return when (val parsed = parser.parse(url.toHttpUrlOrNull() ?: return WebFeedDiscoveryResult.InvalidUrl, html)) {
                is WebFeedParseResult.Success -> WebFeedDiscoveryResult.Preview(parsed.siteTitle, parsed.articles)
                WebFeedParseResult.Unsupported -> WebFeedDiscoveryResult.Unsupported
                WebFeedParseResult.Ambiguous -> WebFeedDiscoveryResult.Ambiguous
                WebFeedParseResult.TooFewCandidates -> WebFeedDiscoveryResult.TooFewCandidates
            }
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
