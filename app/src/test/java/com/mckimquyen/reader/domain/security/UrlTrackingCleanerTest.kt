package com.mckimquyen.reader.domain.security

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlTrackingCleanerTest {

    @Test
    fun clean_removesUtmParametersWhilePreservingValidQueryParams() {
        val input = "https://example.com/article?id=123&utm_source=twitter&utm_medium=social&utm_campaign=spring2026&page=2"
        val expected = "https://example.com/article?id=123&page=2"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_removesSocialAndAdClickIdentifiers() {
        val input = "https://example.com/news?fbclid=IwAR123&gclid=Cj0K456&gbraid=0AAAA789&wbraid=0BBBB&msclkid=abc&twclid=xyz&igshid=def"
        val expected = "https://example.com/news"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_removesGoogleAnalyticsAndEmailTracking() {
        val input = "https://example.com/post?_ga=GA1.2.3.4&_gl=1*abc&mc_cid=c123&mc_eid=e456&_hsenc=h789&_hsmi=m012"
        val expected = "https://example.com/post"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_preservesFragmentAndAnchor() {
        val input = "https://example.com/doc?utm_source=rss&view=full#section-2"
        val expected = "https://example.com/doc?view=full#section-2"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_handlesCleanUrlOrMalformedUrlGracefully() {
        assertEquals("https://example.com/clean/path", UrlTrackingCleaner.clean("https://example.com/clean/path"))
        assertEquals("", UrlTrackingCleaner.clean(""))
        assertEquals("not-a-valid-url", UrlTrackingCleaner.clean("not-a-valid-url"))
    }
}
