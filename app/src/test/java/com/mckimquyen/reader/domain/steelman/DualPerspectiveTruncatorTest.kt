package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPerspectiveTruncatorTest {
    @Test fun shortHeadline_isKept_andNotMarkedCut() {
        val r = DualPerspectiveTruncator.truncate("  Starlink warning  ")
        assertEquals("Starlink warning", r.text)
        assertFalse(r.wasCut)
    }

    @Test fun longHeadline_isCutAtWordBoundary_withEllipsis() {
        val r = DualPerspectiveTruncator.truncate("word ".repeat(100))
        assertTrue(r.wasCut)
        assertTrue(r.text.endsWith("…"))
        assertTrue(r.text.length <= DualPerspectiveTruncator.MAX_HEADLINE_CHARS + 1)
        assertFalse(r.text.dropLast(1).endsWith(" "))
    }

    @Test fun headlineExactlyAtLimit_isNotCut() {
        val text = "a".repeat(DualPerspectiveTruncator.MAX_HEADLINE_CHARS)
        val r = DualPerspectiveTruncator.truncate(text)
        assertEquals(text, r.text)
        assertFalse(r.wasCut)
    }

    @Test fun blankAndWhitespaceInside_areCollapsed() {
        assertEquals("", DualPerspectiveTruncator.truncate("   ").text)
        assertEquals("a b c", DualPerspectiveTruncator.truncate("a \n  b\t c").text)
    }

    @Test fun singleHugeWord_isHardCut() {
        val r = DualPerspectiveTruncator.truncate("x".repeat(500))
        assertTrue(r.wasCut)
        assertEquals(DualPerspectiveTruncator.MAX_HEADLINE_CHARS + 1, r.text.length)
    }
}
