package com.mckimquyen.reader.infrastructure.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {
    @Test
    fun returnsNullForNullOrEmptyText() {
        assertNull(UrlExtractor.extractValidUrl(null))
        assertNull(UrlExtractor.extractValidUrl(""))
        assertNull(UrlExtractor.extractValidUrl("   "))
    }

    @Test
    fun extractsRawHttpsUrl() {
        val input = "https://example.com/story/123"
        assertEquals("https://example.com/story/123", UrlExtractor.extractValidUrl(input))
    }

    @Test
    fun extractsUrlEmbeddedInShareText() {
        val input = "Check out this great article! https://news.test/post?id=456 and let me know."
        assertEquals("https://news.test/post?id=456", UrlExtractor.extractValidUrl(input))
    }

    @Test
    fun ignoresDangerousSchemes() {
        assertNull(UrlExtractor.extractValidUrl("file:///android_asset/secret.txt"))
        assertNull(UrlExtractor.extractValidUrl("javascript:alert(1)"))
        assertNull(UrlExtractor.extractValidUrl("content://media/external/images"))
    }

    @Test
    fun trimsTrailingPunctuation() {
        val input = "Read this: https://example.test/page."
        assertEquals("https://example.test/page", UrlExtractor.extractValidUrl(input))
    }
}
