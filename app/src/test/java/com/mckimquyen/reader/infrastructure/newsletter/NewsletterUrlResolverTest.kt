package com.mckimquyen.reader.infrastructure.newsletter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsletterUrlResolverTest {

    private val resolver = NewsletterUrlResolver()

    @Test
    fun detectPlatform_identifiesPlatformsCorrectly() {
        assertEquals(NewsletterPlatform.SUBSTACK, resolver.detectPlatform("https://danluu.substack.com"))
        assertEquals(NewsletterPlatform.SUBSTACK, resolver.detectPlatform("https://substack.com/@swyx"))
        assertEquals(NewsletterPlatform.MEDIUM, resolver.detectPlatform("https://medium.com/@swyx"))
        assertEquals(NewsletterPlatform.MEDIUM, resolver.detectPlatform("https://swyx.medium.com/post-123"))
        assertEquals(NewsletterPlatform.GHOST, resolver.detectPlatform("https://demo.ghost.io"))
        assertEquals(NewsletterPlatform.BEEHIIV, resolver.detectPlatform("https://daily.beehiiv.com"))
        assertEquals(NewsletterPlatform.GENERIC, resolver.detectPlatform("https://example.com/blog"))
    }

    @Test
    fun resolvePlatformRssUrl_transformsSubstackUrls() {
        assertEquals(
            "https://danluu.substack.com/feed",
            resolver.resolvePlatformRssUrl("https://danluu.substack.com")
        )
        assertEquals(
            "https://danluu.substack.com/feed",
            resolver.resolvePlatformRssUrl("https://danluu.substack.com/p/computer-latency")
        )
        assertEquals(
            "https://danluu.substack.com/feed",
            resolver.resolvePlatformRssUrl("http://danluu.substack.com/")
        )
    }

    @Test
    fun resolvePlatformRssUrl_transformsMediumUrls() {
        assertEquals(
            "https://medium.com/feed/@swyx",
            resolver.resolvePlatformRssUrl("https://medium.com/@swyx")
        )
        assertEquals(
            "https://medium.com/feed/@swyx",
            resolver.resolvePlatformRssUrl("https://medium.com/@swyx/my-first-post-123")
        )
        assertEquals(
            "https://swyx.medium.com/feed",
            resolver.resolvePlatformRssUrl("https://swyx.medium.com")
        )
        assertEquals(
            "https://medium.com/feed/towards-data-science",
            resolver.resolvePlatformRssUrl("https://medium.com/towards-data-science")
        )
    }

    @Test
    fun resolvePlatformRssUrl_transformsGhostUrls() {
        assertEquals(
            "https://demo.ghost.io/rss/",
            resolver.resolvePlatformRssUrl("https://demo.ghost.io")
        )
        assertEquals(
            "https://demo.ghost.io/rss/",
            resolver.resolvePlatformRssUrl("https://demo.ghost.io/welcome/")
        )
    }

    @Test
    fun resolvePlatformRssUrl_transformsBeehiivUrls() {
        assertEquals(
            "https://daily.beehiiv.com/feed",
            resolver.resolvePlatformRssUrl("https://daily.beehiiv.com")
        )
        assertEquals(
            "https://daily.beehiiv.com/feed",
            resolver.resolvePlatformRssUrl("https://daily.beehiiv.com/p/morning-edition")
        )
    }

    @Test
    fun extractRssLinkFromHtml_findsAlternateRssOrAtomLinks() {
        val htmlWithRss = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Independent Tech Newsletter</title>
                <link rel="alternate" type="application/rss+xml" title="RSS Feed" href="/rss.xml" />
            </head>
            <body><p>Content</p></body>
            </html>
        """.trimIndent()

        val resolved = resolver.extractRssLinkFromHtml(htmlWithRss, "https://mytechblog.com/articles/")
        assertEquals("https://mytechblog.com/rss.xml", resolved)

        val htmlWithAtom = """
            <html>
            <head>
                <link rel="alternate" type="application/atom+xml" href="https://atom.example.org/feed.atom" />
            </head>
            </html>
        """.trimIndent()

        val resolvedAtom = resolver.extractRssLinkFromHtml(htmlWithAtom, "https://example.org/")
        assertEquals("https://atom.example.org/feed.atom", resolvedAtom)
    }

    @Test
    fun extractRssLinkFromHtml_returnsNullWhenNoFeedLink() {
        val htmlNoFeed = "<html><head><title>No Feed</title></head><body>Hello</body></html>"
        assertNull(resolver.extractRssLinkFromHtml(htmlNoFeed, "https://example.com/"))
    }
}
