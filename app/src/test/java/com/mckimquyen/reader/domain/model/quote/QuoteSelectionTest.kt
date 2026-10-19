package com.mckimquyen.reader.domain.model.quote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteSelectionTest {

    @Test
    fun sanitize_nullAndBlank_returnNull() {
        assertNull(QuoteSelection.sanitize(null))
        assertNull(QuoteSelection.sanitize(""))
        assertNull(QuoteSelection.sanitize("   \n\t "))
    }

    @Test
    fun sanitize_collapsesWhitespaceAndTrims() {
        assertEquals("hello world", QuoteSelection.sanitize("  hello   world  "))
    }

    @Test
    fun sanitize_joinsLinesWithSingleSpace() {
        assertEquals("line1 line2 line3", QuoteSelection.sanitize("line1\n\nline2\r\nline3"))
    }

    @Test
    fun sanitize_treatsNonBreakingSpaceAsSpace() {
        assertEquals("a b", QuoteSelection.sanitize("a b"))
    }
}
