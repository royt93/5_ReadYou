package com.mckimquyen.reader.domain.model.notebook

import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightColorTest {

    @Test
    fun fromHex_validColors_returnsCorrectEnum() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("#FFF176"))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("#fff176"))
        assertEquals(HighlightColor.BLUE, HighlightColor.fromHex("#81D4FA"))
        assertEquals(HighlightColor.PINK, HighlightColor.fromHex("#F48FB1"))
        assertEquals(HighlightColor.GREEN, HighlightColor.fromHex("#A5D6A7"))
        assertEquals(HighlightColor.ORANGE, HighlightColor.fromHex("#FFCC80"))
    }

    @Test
    fun fromHex_invalidOrUnknownColor_fallsBackToYellow() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("#000000"))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("invalid"))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex(""))
    }
}
