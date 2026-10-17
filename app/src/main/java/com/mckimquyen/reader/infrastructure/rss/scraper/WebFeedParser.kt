package com.mckimquyen.reader.infrastructure.rss.scraper

import java.net.URI
import javax.inject.Inject
import java.util.Date
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class WebArticleCandidate(
    val title: String,
    val url: HttpUrl,
    val publishedAt: Date?,
)

sealed interface WebFeedParseResult {
    data class Success(
        val siteTitle: String,
        val articles: List<WebArticleCandidate>,
    ) : WebFeedParseResult

    data object Unsupported : WebFeedParseResult
    data object Ambiguous : WebFeedParseResult
    data object TooFewCandidates : WebFeedParseResult
}

class WebFeedParser @Inject constructor() {
    fun parse(sourceUrl: HttpUrl, html: String): WebFeedParseResult {
        val document = Jsoup.parse(html, sourceUrl.toString())
        val groups = document.select("article")
            .mapNotNull { element -> articleCandidate(element, sourceUrl) }
            .distinctBy { it.url.toString() }
        if (groups.size >= MINIMUM_ARTICLE_COUNT) {
            return WebFeedParseResult.Success(siteTitle(document, sourceUrl), groups)
        }
        val fallback = findRepeatedLinkGroup(document, sourceUrl)
        return fallback ?: if (groups.isNotEmpty()) WebFeedParseResult.TooFewCandidates else WebFeedParseResult.Unsupported
    }

    private fun findRepeatedLinkGroup(document: org.jsoup.nodes.Document, sourceUrl: HttpUrl): WebFeedParseResult? {
        val candidates = document.select("a[href]")
            .mapNotNull { anchor -> anchorCandidate(anchor, sourceUrl) }
        val distinct = candidates.distinctBy { it.article.url.toString() }
        if (distinct.size < MINIMUM_ARTICLE_COUNT) return null
        val byParent = distinct.groupBy { it.parentSelector }
            .values
            .filter { it.size >= MINIMUM_ARTICLE_COUNT }
        if (byParent.isEmpty()) return null
        val largestSize = byParent.maxOf { it.size }
        val strongest = byParent.filter { it.size == largestSize }
        if (strongest.size != 1 || distinct.size > largestSize) return WebFeedParseResult.Ambiguous
        return WebFeedParseResult.Success(siteTitle(document, sourceUrl), strongest.single().map { it.article })
    }

    private fun articleCandidate(element: Element, sourceUrl: HttpUrl): WebArticleCandidate? {
        val heading = element.selectFirst("h1, h2, h3, h4") ?: return null
        val title = heading.text().trim().takeIf(String::isNotBlank) ?: return null
        val url = (heading.select("a[href]") + element.select("a[href]"))
            .firstNotNullOfOrNull { canonicalUrl(it.attr("href"), sourceUrl)?.takeUnless(::isMediaUrl) }
            ?: return null
        val date = element.selectFirst("time[datetime]")?.attr("datetime")?.let(::parseDate)
        return WebArticleCandidate(title, url, date)
    }

    private fun anchorCandidate(anchor: Element, sourceUrl: HttpUrl): LinkCandidate? {
        if (anchor.parents().any { it.tagName() in NON_ARTICLE_CONTAINERS }) return null
        val title = anchor.text().trim().takeIf(String::isNotBlank) ?: return null
        if (title.length > MAX_CANDIDATE_TITLE_LENGTH) return null
        val url = canonicalUrl(anchor.attr("href"), sourceUrl)?.takeUnless(::isMediaUrl) ?: return null
        val date = anchor.parents().firstNotNullOfOrNull { parent ->
            parent.selectFirst("time[datetime]")?.attr("datetime")?.let(::parseDate)
        }
        val listContainer = anchor.parents().firstOrNull { it.id().isNotBlank() || it.classNames().isNotEmpty() }
            ?: anchor.parent()
        val parentSelector = listContainer?.tagName().orEmpty() + "#" + listContainer?.id().orEmpty() + "|" +
            listContainer?.classNames().orEmpty().sorted().joinToString(".")
        return LinkCandidate(WebArticleCandidate(title, url, date), parentSelector)
    }

    private fun canonicalUrl(href: String, sourceUrl: HttpUrl): HttpUrl? {
        val uri = runCatching { URI(sourceUrl.resolve(href)?.toString() ?: return null) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.host.isNullOrBlank()) return null
        val resolved = sourceUrl.resolve(href) ?: return null
        return resolved.newBuilder().fragment(null).build()
    }

    // Article pages only: audio/video/archives are never HTML article bodies (NPR .mp3 was stored as 15.9 MB).
    private fun isMediaUrl(url: HttpUrl): Boolean =
        url.encodedPath.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS

    private fun siteTitle(document: org.jsoup.nodes.Document, sourceUrl: HttpUrl): String =
        document.selectFirst("meta[property=og:site_name]")?.attr("content")?.trim()?.takeIf(String::isNotBlank)
            ?: document.title().trim().takeIf(String::isNotBlank)
            ?: sourceUrl.host

    private fun parseDate(value: String): Date? =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()?.let(::Date)

    private data class LinkCandidate(val article: WebArticleCandidate, val parentSelector: String)

    companion object {
        const val MINIMUM_ARTICLE_COUNT = 3
        const val MAX_CANDIDATE_TITLE_LENGTH = 180
        val NON_ARTICLE_CONTAINERS = setOf("nav", "aside", "header", "footer")
        val MEDIA_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "mp4", "m4v", "mov", "webm", "mkv", "avi",
            "pdf", "zip", "gz", "rar", "7z", "apk", "exe", "dmg",
        )
    }
}
