package com.mckimquyen.reader.domain.model.article

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioUrlCandidatesTest {
    @Test fun httpTriesHttpsFirstThenOriginal() =
        assertEquals(listOf("https://x/e.mp3", "http://x/e.mp3"), AudioUrlCandidates.of("http://x/e.mp3"))

    @Test fun httpsIsKeptAsIs() = assertEquals(listOf("https://x/e.mp3"), AudioUrlCandidates.of("https://x/e.mp3"))

    @Test fun schemeIsMatchedCaseInsensitively() =
        assertEquals(listOf("https://x/e.mp3", "HTTP://x/e.mp3"), AudioUrlCandidates.of("HTTP://x/e.mp3"))

    @Test fun queryStringIsPreserved() =
        assertEquals("https://x/e.mp3?a=1&b=2", AudioUrlCandidates.of("http://x/e.mp3?a=1&b=2").first())
}
