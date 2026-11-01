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
    fun bodyFraction_singleItemBody_usesScrollOffsetInsideTheItem() {
        assertEquals(0.5f, ReadingPositionMapper.bodyFractionFromList(1, 500, 1000, 1, 1), 0.0001f)
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(1, 0, 1000, 1, 1), 0f)
    }

    @Test
    fun bodyFraction_multiItemBody_addsOffsetFractionToItemIndex() {
        assertEquals(0.3125f, ReadingPositionMapper.bodyFractionFromList(3, 250, 1000, 2, 4), 0.0001f)
    }

    @Test
    fun bodyFraction_beforeAndAfterBody_andEmptyInputs_goToEdges() {
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(1, 0, 100, 3, 4), 0f)
        assertEquals(1f, ReadingPositionMapper.bodyFractionFromList(8, 0, 100, 3, 4), 0f)
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(5, 10, 0, 3, 0), 0f)
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(3, 10, 0, 3, 2), 0f)
    }

    @Test
    fun scrollTarget_splitsFractionIntoItemAndOffsetInsideIt() {
        val multi = ReadingPositionMapper.scrollTargetFromBodyFraction(0.5f, 2, 6)
        assertEquals(5, multi.listIndex)
        assertEquals(0f, multi.withinItem, 0.0001f)

        val single = ReadingPositionMapper.scrollTargetFromBodyFraction(0.25f, 1, 1)
        assertEquals(1, single.listIndex)
        assertEquals(0.25f, single.withinItem, 0.0001f)
    }

    @Test
    fun scrollTarget_clampsFraction_andHandlesEmptyBody() {
        val low = ReadingPositionMapper.scrollTargetFromBodyFraction(-1f, 3, 2)
        assertEquals(3, low.listIndex)
        assertEquals(0f, low.withinItem, 0f)

        val high = ReadingPositionMapper.scrollTargetFromBodyFraction(5f, 3, 2)
        assertEquals(4, high.listIndex)
        assertEquals(1f, high.withinItem, 0f)

        val empty = ReadingPositionMapper.scrollTargetFromBodyFraction(0.5f, 3, 0)
        assertEquals(3, empty.listIndex)
        assertEquals(0f, empty.withinItem, 0f)
    }

    @Test
    fun maps_body_fraction_back_to_global_list_fraction() {
        assertEquals(5f / 9f, ReadingPositionMapper.listFractionFromBodyFraction(0.5f, 2, 6, 10), 0.0001f)
        assertEquals(0f, ReadingPositionMapper.listFractionFromBodyFraction(0.5f, 2, 6, 1), 0f)
    }

    @Test
    fun tokenMapping_clampsAndHandlesEmpty() {
        assertEquals(0, ReadingPositionMapper.tokenIndexFromFraction(0.5f, 0))
        assertEquals(0f, ReadingPositionMapper.fractionFromTokenIndex(0, 0), 0f)
    }

    @Test
    fun maps_fraction_to_token_and_back() {
        assertEquals(4, ReadingPositionMapper.tokenIndexFromFraction(0.5f, 9))
        assertEquals(0.5f, ReadingPositionMapper.fractionFromTokenIndex(4, 9), 0.0001f)
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
