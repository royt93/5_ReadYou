package com.mckimquyen.reader.domain.readposition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ReadingPositionMapperTest {

    @Test
    fun maps_body_fraction_to_body_list_index() {
        assertEquals(4, ReadingPositionMapper.listIndexFromBodyFraction(0.5f, 2, 6))
    }

    @Test
    fun maps_body_fraction_back_to_global_list_fraction() {
        assertEquals(4f / 9f, ReadingPositionMapper.listFractionFromBodyFraction(0.5f, 2, 6, 10), 0.0001f)
    }

    @Test
    fun maps_fraction_to_token_and_back() {
        assertEquals(4, ReadingPositionMapper.tokenIndexFromFraction(0.5f, 9))
        assertEquals(0.5f, ReadingPositionMapper.fractionFromTokenIndex(4, 9), 0.0001f)
    }

    @Test
    fun clamps_fraction_and_empty_ranges() {
        assertEquals(0, ReadingPositionMapper.listIndexFromBodyFraction(-1f, 3, 2).let { it - 3 })
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(5, 3, 0), 0f)
        assertEquals(0, ReadingPositionMapper.tokenIndexFromFraction(0.5f, 0))
        assertEquals(0f, ReadingPositionMapper.fractionFromTokenIndex(0, 0), 0f)
    }

    @Test
    fun maps_positions_before_and_after_body_to_edges() {
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(1, 3, 4), 0f)
        assertEquals(1f, ReadingPositionMapper.bodyFractionFromList(8, 3, 4), 0f)
    }

    @Test
    fun paragraphOffset_uses_nearest_preceding_blank_line_boundary() {
        val text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        assertEquals(text.indexOf("Second"), ReadingPositionMapper.paragraphStartOffset(text, 0.5f))
    }

    @Test
    fun paragraphOffset_handles_no_break_empty_text_and_fraction_edges() {
        assertEquals(0, ReadingPositionMapper.paragraphStartOffset("One line only", 0.8f))
        assertEquals(0, ReadingPositionMapper.paragraphStartOffset("", 0.5f))
        assertEquals(0, ReadingPositionMapper.paragraphStartOffset("A\n\nB", 0f))
        assertEquals("A\n\n".length, ReadingPositionMapper.paragraphStartOffset("A\n\nB", 1f))
    }

    @Test
    fun shouldReturnToReader_onlyAfterMinimumRsvpAdvance() {
        assertFalse(ReadingPositionMapper.shouldHandoffFromRsvp(startIndex = 10, currentIndex = 29))
        assertTrue(ReadingPositionMapper.shouldHandoffFromRsvp(startIndex = 10, currentIndex = 30))
    }
}
