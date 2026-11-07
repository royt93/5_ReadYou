package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedUrlNormalizerTest {
    @Test fun schemeAndHostLowercaseFragmentDefaultPortAndTrailingSlashNormalize() {
        assertEquals("https://example.org/rss", FeedUrlNormalizer.normalize("HTTPS://EXAMPLE.ORG:443/rss/#part"))
        assertEquals("http://example.org/", FeedUrlNormalizer.normalize("HTTP://Example.org:80/#f"))
        assertEquals("https://example.org/rss", FeedUrlNormalizer.normalize("https://Example.org/rss/"))
    }

    @Test fun nonDefaultPortPathCaseAndQueryArePreserved() {
        assertEquals("https://example.org:8443/RSS?x=1", FeedUrlNormalizer.normalize("https://EXAMPLE.org:8443/RSS?x=1#top"))
        assertNotEquals(FeedUrlNormalizer.normalize("https://example.org/A"), FeedUrlNormalizer.normalize("https://example.org/a"))
    }

    @Test fun percentEncodedPathAndQueryAreKeptVerbatim() {
        assertEquals("https://e.org/a%2Fb/c%20d?q=%26x%3D1", FeedUrlNormalizer.normalize("https://e.org/a%2Fb/c%20d?q=%26x%3D1"))
        assertEquals("https://e.org/100%25", FeedUrlNormalizer.normalize("https://e.org/100%25"))
    }

    @Test fun normalizingTwiceChangesNothing() {
        listOf(
            "HTTPS://EXAMPLE.ORG:443/a%2Fb/?q=%20#frag",
            "https://e.org/é/ü?x=ß",
            "http://192.168.1.5:8080/feed%20one?id=1%2B2",
            "https://e.org",
        ).forEach {
            val once = FeedUrlNormalizer.normalize(it)
            assertEquals(it, once, FeedUrlNormalizer.normalize(once!!))
        }
    }

    @Test fun nonAsciiIsEncodedExactlyOnce() {
        assertEquals("https://e.org/%C3%A9", FeedUrlNormalizer.normalize("https://e.org/é"))
        assertEquals("https://e.org/%C3%A9", FeedUrlNormalizer.normalize("https://e.org/%C3%A9"))
    }

    @Test fun queryIsIdentityButFragmentIsNot() {
        assertEquals(FeedUrlNormalizer.normalize("https://e.org/f?id=1#a"), FeedUrlNormalizer.normalize("https://e.org/f?id=1#b"))
        assertNotEquals(FeedUrlNormalizer.normalize("https://e.org/f?id=1"), FeedUrlNormalizer.normalize("https://e.org/f?id=2"))
    }

    @Test fun onlyHttpAndHttpsAreAccepted() {
        listOf("file:///etc/passwd", "ftp://example.org/rss", "javascript:alert(1)", "content://x/y").forEach {
            assertNull(it, FeedUrlNormalizer.normalize(it))
        }
    }

    @Test fun malformedRelativeAndHostlessUrlsAreRejected() {
        listOf("", " ", "https:", "/feed.xml", "example.org/rss", "https:///rss", "http://").forEach {
            assertNull(it, FeedUrlNormalizer.normalize(it))
        }
    }

    @Test fun credentialsAreRejectedNotStripped() {
        assertNull(FeedUrlNormalizer.normalize("https://user:secret@example.org/rss"))
        assertNull(FeedUrlNormalizer.normalize("https://user@example.org/rss"))
    }

    @Test fun controlsWhitespaceAndOversizeInputAreRejected() {
        listOf("https://e.org/a\nb", "https://e.org/\u0000x", "https://e.org/a b").forEach {
            assertNull(FeedUrlNormalizer.normalize(it))
        }
        assertNull(FeedUrlNormalizer.normalize("https://e.org/" + "a".repeat(SyncLimits.MAX_FEED_URL_CHARS)))
    }

    @Test fun privateLanHttpIsAllowedButNeverFetchedByNormalizer() {
        assertEquals("http://192.168.1.5:8080/feed", FeedUrlNormalizer.normalize("HTTP://192.168.1.5:8080/feed"))
    }
}
