package com.mckimquyen.reader.infrastructure.newsletter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsletterUrlResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    // Secondary constructor for unit testing without DI
    constructor() : this(defaultOkHttpClient)

    fun detectPlatform(url: String): NewsletterPlatform {
        val parsed = url.toHttpUrlOrNull() ?: return NewsletterPlatform.GENERIC
        val host = parsed.host.lowercase()

        return when {
            host.endsWith("substack.com") -> NewsletterPlatform.SUBSTACK
            host == "medium.com" || host.endsWith(".medium.com") -> NewsletterPlatform.MEDIUM
            host.endsWith("ghost.io") -> NewsletterPlatform.GHOST
            host.endsWith("beehiiv.com") -> NewsletterPlatform.BEEHIIV
            else -> NewsletterPlatform.GENERIC
        }
    }

    fun resolvePlatformRssUrl(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        val pathSegments = parsed.pathSegments.filter { it.isNotBlank() }

        // Substack: subdomain.substack.com -> https://subdomain.substack.com/feed
        if (host.endsWith(".substack.com") && host != "www.substack.com") {
            return "https://$host/feed"
        }

        // Medium
        if (host == "medium.com") {
            if (pathSegments.isNotEmpty()) {
                val firstSegment = pathSegments.first()
                if (firstSegment.startsWith("@")) {
                    return "https://medium.com/feed/$firstSegment"
                }
                if (firstSegment !in MEDIUM_SYSTEM_PATHS) {
                    return "https://medium.com/feed/$firstSegment"
                }
            }
        } else if (host.endsWith(".medium.com")) {
            return "https://$host/feed"
        }

        // Ghost: *.ghost.io -> https://*.ghost.io/rss/
        if (host.endsWith(".ghost.io")) {
            return "https://$host/rss/"
        }

        // Beehiiv: *.beehiiv.com -> https://*.beehiiv.com/feed
        if (host.endsWith(".beehiiv.com")) {
            return "https://$host/feed"
        }

        return null
    }

    fun extractRssLinkFromHtml(html: String, baseUrl: String): String? {
        if (html.isBlank()) return null
        return runCatching {
            val doc = Jsoup.parse(html, baseUrl)
            val feedLinkElement = doc.select(
                "link[rel=\"alternate\"][type=\"application/rss+xml\"], " +
                        "link[rel=\"alternate\"][type=\"application/atom+xml\"], " +
                        "link[rel=\"alternate\"][type=\"application/json\"]"
            ).firstOrNull() ?: return null

            val absUrl = feedLinkElement.absUrl("href")
            if (absUrl.isNotBlank() && (absUrl.startsWith("http://") || absUrl.startsWith("https://"))) {
                absUrl
            } else {
                val rawHref = feedLinkElement.attr("href")
                val baseParsed = baseUrl.toHttpUrlOrNull()
                baseParsed?.resolve(rawHref)?.toString()
            }
        }.getOrNull()
    }

    suspend fun resolveToRssUrl(inputUrl: String): String? = withContext(Dispatchers.IO) {
        val platformRss = resolvePlatformRssUrl(inputUrl)
        if (platformRss != null) return@withContext platformRss

        // Fallback: discover from HTML <link rel="alternate">
        runCatching {
            val request = Request.Builder()
                .url(inputUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val source = body.source()
                val htmlHead = source.readUtf8(MAX_HEAD_BYTES.coerceAtMost(body.contentLength().takeIf { it > 0 } ?: MAX_HEAD_BYTES))
                extractRssLinkFromHtml(htmlHead, inputUrl)
            }
        }.getOrNull()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Android; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0 RSSCatHub"
        private const val MAX_HEAD_BYTES = 262_144L // 256 KiB
        private val MEDIUM_SYSTEM_PATHS = setOf("tag", "topic", "search", "m", "me", "about", "membership")

        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}
