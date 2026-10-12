package com.mckimquyen.reader.infrastructure.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DailyDigestParserTest {
    @Test
    fun parse_validDigest_keepsOnlyKnownSourceIds() {
        val result = DailyDigestService.parse(
            """{"headline":"Today's brief","sections":[{"title":"Top","items":[{"text":"Story","sourceIds":["a1","not-in-input"]}]}]}""",
            validSourceIds = setOf("a1", "a2"),
        )

        assertEquals("Today's brief", result.headline)
        assertEquals(listOf("a1"), result.sections.single().items.single().sourceIds)
    }

    @Test
    fun parse_geminiMarkdownJsonWithSourceReferences_recoversAndKeepsKnownSources() {
        val result = DailyDigestService.parse(
            """```json
                {"headline":"Daily brief","sections":[{"title":"Top","items":[{"text":"Story","sourceIds":["a1","unknown"]}]}]}
                ```""",
            validSourceIds = setOf("a1"),
        )

        assertEquals("Daily brief", result.headline)
        assertEquals(listOf("a1"), result.sections.single().items.single().sourceIds)
    }

    @Test
    fun parse_malformedJson_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyDigestService.parse("not-json", setOf("a1"))
        }
    }

    @Test
    fun parse_emptySections_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyDigestService.parse("""{"headline":"Brief","sections":[]}""", setOf("a1"))
        }
    }
}
