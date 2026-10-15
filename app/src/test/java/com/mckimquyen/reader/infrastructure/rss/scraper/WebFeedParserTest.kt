package com.mckimquyen.reader.infrastructure.rss.scraper

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebFeedParserTest {
    @Test
    fun parsesRepeatedArticleElementsAndRelativeLinksInDocumentOrder() {
        val html = """<html><head><title>Example Journal</title></head><body>
            <article><h2><a href="/story/1#top">First report</a></h2><time datetime="2026-10-14T09:00:00Z"></time></article>
            <article><h2><a href="https://news.example/story/2?utm_source=home">Second report</a></h2><time datetime="not-a-date"></time></article>
            <article><h2><a href="/story/3">Third report</a></h2></article>
            </body></html>"""

        val result = WebFeedParser.parse("https://news.example/world/".toHttpUrl(), html)

        assertTrue(result is WebFeedParseResult.Success)
        val success = result as WebFeedParseResult.Success
        assertEquals("Example Journal", success.siteTitle)
        assertEquals(listOf("First report", "Second report", "Third report"), success.articles.map { it.title })
        assertEquals(
            listOf(
                "https://news.example/story/1",
                "https://news.example/story/2?utm_source=home",
                "https://news.example/story/3",
            ),
            success.articles.map { it.url.toString() },
        )
        assertEquals("2026-10-14T09:00:00Z", success.articles.first().publishedAt?.toInstant()?.toString())
        assertEquals(null, success.articles[1].publishedAt)
        assertEquals("news.example", success.articles.first().url.host)
    }

    @Test
    fun duplicateCanonicalUrlsDoNotCountTowardMinimumCandidateThreshold() {
        val html = """<html><body>
            <article><h2><a href="/same#first">First</a></h2></article>
            <article><h2><a href="/same#second">Duplicate</a></h2></article>
            <article><h2><a href="/other">Other</a></h2></article>
            </body></html>"""

        val result = WebFeedParser.parse("https://news.example/".toHttpUrl(), html)

        assertEquals(WebFeedParseResult.TooFewCandidates, result)
    }

    @Test
    fun rejectsPagesWithoutThreeValidArticlesAndSkipsMalformedCandidates() {
        val html = """<html><body>
            <article><h2><a href="/one">One</a></h2></article>
            <article><h2><a href="javascript:alert(1)">Unsafe</a></h2></article>
            <article><h2>No link</h2></article>
            <article><a href="/four">No title</a></article>
            </body></html>"""

        assertEquals(WebFeedParseResult.TooFewCandidates, WebFeedParser.parse("https://news.example/".toHttpUrl(), html))
    }

    @Test
    fun returnsAmbiguousWhenTwoRepeatedListsHaveEqualCandidateCounts() {
        val html = """<html><body>
            <ul class="latest"><li><a href="/a1">Latest one</a></li><li><a href="/a2">Latest two</a></li><li><a href="/a3">Latest three</a></li></ul>
            <ul class="popular"><li><a href="/p1">Popular one</a></li><li><a href="/p2">Popular two</a></li><li><a href="/p3">Popular three</a></li></ul>
            </body></html>"""

        val result = WebFeedParser.parse("https://news.example/".toHttpUrl(), html)

        assertEquals(WebFeedParseResult.Ambiguous, result)
    }

    @Test
    fun ignoresNavigationAndSidebarLinksWhenArticleElementsExist() {
        val html = """<html><head><title>News</title></head><body>
            <nav><a href="/nav1">Nav one</a><a href="/nav2">Nav two</a><a href="/nav3">Nav three</a></nav>
            <aside><a href="/side1">Side one</a><a href="/side2">Side two</a><a href="/side3">Side three</a></aside>
            <article><h2><a href="/a">Article one</a></h2></article>
            <article><h2><a href="/b">Article two</a></h2></article>
            <article><h2><a href="/c">Article three</a></h2></article>
            </body></html>"""

        val result = WebFeedParser.parse("https://news.example/".toHttpUrl(), html) as WebFeedParseResult.Success

        assertEquals(listOf("Article one", "Article two", "Article three"), result.articles.map { it.title })
    }
}
