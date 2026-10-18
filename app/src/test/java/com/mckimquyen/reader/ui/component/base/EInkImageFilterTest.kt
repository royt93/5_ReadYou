package com.mckimquyen.reader.ui.component.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EInkImageFilterTest {

    @Test
    fun eInkOff_returnsNoFilter() {
        assertNull(eInkImageColorFilter(enabled = false))
    }

    @Test
    fun eInkOn_returnsFilter() {
        assertNotNull(eInkImageColorFilter(enabled = true))
    }

    @Test
    fun grayscaleMatrix_mapsEveryChannelToSameLuminanceAndKeepsAlpha() {
        val values = grayscaleColorMatrix().values

        // Rows 0..2 (R, G, B outputs) must be identical => R == G == B => gray.
        for (column in 0 until MATRIX_COLUMNS) {
            assertEquals(values[column], values[MATRIX_COLUMNS + column], 0f)
            assertEquals(values[column], values[2 * MATRIX_COLUMNS + column], 0f)
        }
        // Luminance weights sum to ~1 so white stays white.
        assertEquals(1f, values[0] + values[1] + values[2], 0.01f)
        // Alpha row is identity.
        assertEquals(1f, values[3 * MATRIX_COLUMNS + 3], 0f)
    }

    private companion object {
        const val MATRIX_COLUMNS = 5
    }
}
