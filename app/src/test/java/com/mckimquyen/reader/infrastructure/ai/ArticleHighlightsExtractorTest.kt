package com.mckimquyen.reader.infrastructure.ai

import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHighlightsExtractorTest {

    @Test
    fun calculateReadingTimeSaved_returnsSensibleMinutes() {
        // Article with 1100 words (~5 min reading time at 220 wpm)
        // Summary with 110 words (~1 min reading time)
        // Time saved = 5 - 1 = 4 min
        val saved = ArticleHighlightsExtractor.calculateReadingTimeSaved(1100, 110)
        assertEquals(4, saved)

        // Very short article (100 words), summary (30 words)
        // Saved should be coerced to at least 1 min
        val savedShort = ArticleHighlightsExtractor.calculateReadingTimeSaved(100, 30)
        assertTrue(savedShort >= 1)
    }

    @Test
    fun parseGeminiResponse_validJson_extractsAllFields() {
        val sampleJson = """
            {
              "tldr": "Android 17 brings revolutionary spatial audio and predictive back gestures.",
              "takeaways": [
                "Enhanced predictive back gesture animations across all apps.",
                "Spatial audio rendering with dynamic head tracking API.",
                "Performance optimizations reducing idle battery drain by 15%."
              ],
              "tags": ["Android17", "Performance", "Audio"]
            }
        """.trimIndent()

        val highlights = ArticleHighlightsExtractor.parseGeminiResponse(sampleJson, totalWordCount = 800)

        assertEquals("Android 17 brings revolutionary spatial audio and predictive back gestures.", highlights.tldr)
        assertEquals(3, highlights.keyTakeaways.size)
        assertEquals("Enhanced predictive back gesture animations across all apps.", highlights.keyTakeaways[0])
        assertEquals("Performance optimizations reducing idle battery drain by 15%.", highlights.keyTakeaways[2])
        assertEquals(listOf("Android17", "Performance", "Audio"), highlights.tags)
        assertFalse(highlights.isOfflineFallback)
        assertTrue(highlights.readingTimeSavedMin >= 1)
    }

    @Test
    fun parseGeminiResponse_markdownWrappedJson_sanitizesCorrectly() {
        val markdownWrapped = """
            ```json
            {
              "tldr": "Jetpack Compose 1.8 delivers blazing fast text rendering.",
              "takeaways": [
                "TextLayoutCache accelerates complex paragraph layouts.",
                "Drastically cuts down recomposition overhead in lazy lists."
              ],
              "tags": ["Compose", "UI", "Performance"]
            }
            ```
        """.trimIndent()

        val highlights = ArticleHighlightsExtractor.parseGeminiResponse(markdownWrapped, totalWordCount = 600)

        assertEquals("Jetpack Compose 1.8 delivers blazing fast text rendering.", highlights.tldr)
        assertEquals(2, highlights.keyTakeaways.size)
        assertEquals(listOf("Compose", "UI", "Performance"), highlights.tags)
        assertFalse(highlights.isOfflineFallback)
    }

    @Test
    fun parseGeminiResponse_bulletPointsFallback_parsesLines() {
        val rawBullets = """
            Here is the summary of the latest AI breakthroughs:
            - Google Gemini 2.5 Flash achieves record benchmark performance.
            - Low latency response allows instant conversational queries.
            - Obfuscated failover keys ensure zero downtime for clients.
        """.trimIndent()

        val highlights = ArticleHighlightsExtractor.parseGeminiResponse(rawBullets, totalWordCount = 900)

        assertEquals(3, highlights.keyTakeaways.size)
        assertEquals("Google Gemini 2.5 Flash achieves record benchmark performance.", highlights.keyTakeaways[0])
        assertEquals("Obfuscated failover keys ensure zero downtime for clients.", highlights.keyTakeaways[2])
        assertFalse(highlights.isOfflineFallback)
    }

    @Test
    fun extractOfflineHighlights_withRichArticle_producesHighQualityTakeaways() {
        val title = "The Evolution of Mobile RSS Readers in 2026"
        val article = """
            RSS readers have witnessed a tremendous renaissance in 2026. With algorithmic feeds causing content fatigue, readers are returning to open RSS and Atom syndication formats.
            
            Modern RSS applications now combine local-first storage with smart client-side heuristics. By running lightweight natural language models directly on the device, readers can prioritize breaking news without compromising personal privacy.
            
            Battery consumption and offline capability remain paramount for modern commuters. New background synchronization protocols deliver instantaneous updates while consuming under 2% battery daily.
            
            In conclusion, decentralized feeds provide a calmer, highly productive reading environment for power users worldwide.
        """.trimIndent()

        val highlights = ArticleHighlightsExtractor.extractOfflineHighlights(title, article)

        assertTrue(highlights.isOfflineFallback)
        assertTrue(highlights.tldr.isNotBlank())
        assertTrue("Should extract at least 2 takeaways", highlights.keyTakeaways.size >= 2)
        assertTrue("Reading time saved should be positive", highlights.readingTimeSavedMin >= 1)
        assertTrue("Tags should be discovered", highlights.tags.isNotEmpty())
    }

    @Test
    fun extractTags_findsRelevantKeywordsAndFiltersStopWords() {
        val title = "Kotlin Multiplatform and Jetpack Compose Architecture"
        val text = "Kotlin Multiplatform enables code sharing across Android and iOS platforms. Jetpack Compose provides dynamic declarative UI."

        val tags = ArticleHighlightsExtractor.extractTags(text, title, maxTags = 3)

        assertTrue("Tags should include Kotlin or Compose", tags.any { it.contains("Kotlin", ignoreCase = true) || it.contains("Compose", ignoreCase = true) })
        assertFalse("Stop words should not be tags", tags.any { it.equals("and", ignoreCase = true) || it.equals("the", ignoreCase = true) })
    }

    @Test
    fun formatAsPlainText_formatsMarkdownStructureCleanly() {
        val highlights = ArticleHighlights(
            tldr = "Summary of Kotlin features.",
            keyTakeaways = listOf("Smart casts improved", "Context parameters added"),
            readingTimeSavedMin = 3,
            tags = listOf("Kotlin", "Android"),
            isOfflineFallback = false,
        )

        val text = highlights.formatAsPlainText()

        assertTrue(text.contains("📌 TL;DR:"))
        assertTrue(text.contains("Summary of Kotlin features."))
        assertTrue(text.contains("💡 Key Takeaways:"))
        assertTrue(text.contains("1. Smart casts improved"))
        assertTrue(text.contains("2. Context parameters added"))
        assertTrue(text.contains("⏱ Estimated time saved: ~3 min"))
        assertTrue(text.contains("#Kotlin #Android"))
    }
}
