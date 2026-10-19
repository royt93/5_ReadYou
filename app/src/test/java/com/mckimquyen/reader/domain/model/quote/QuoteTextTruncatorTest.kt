package com.mckimquyen.reader.domain.model.quote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteTextTruncatorTest {

    private val max = QuoteTextTruncator.QUOTE_MAX_CHARS
    private val ellipsis = QuoteTextTruncator.ELLIPSIS

    @Test
    fun truncate_exactlyAtLimit_isUntouched() {
        val text = "a".repeat(max)
        assertEquals(Truncated(text, false), QuoteTextTruncator.truncate(text))
    }

    @Test
    fun truncate_shortText_isUntouched() {
        assertEquals(Truncated("short", false), QuoteTextTruncator.truncate("short"))
    }

    @Test
    fun truncate_cutsOnWordBoundaryAndAppendsEllipsis() {
        // Từ 9 ký tự + 1 dấu cách = 10 ký tự mỗi cụm, tổng 300 ký tự.
        val text = List(30) { "abcdefghi" }.joinToString(" ")

        val result = QuoteTextTruncator.truncate(text)

        assertTrue(result.wasCut)
        assertTrue(result.text.endsWith(ellipsis))
        val body = result.text.removeSuffix(ellipsis)
        assertTrue("không được vượt giới hạn: ${body.length}", body.length <= max)
        assertTrue("phải cắt đúng ranh giới từ", body.split(" ").all { it == "abcdefghi" })
    }

    @Test
    fun truncate_singleVeryLongWord_hardCutsAtLimit() {
        val result = QuoteTextTruncator.truncate("x".repeat(400))

        assertTrue(result.wasCut)
        assertEquals("x".repeat(max) + ellipsis, result.text)
    }

    @Test
    fun truncate_neverEndsWithLoneHighSurrogate() {
        val text = "a".repeat(max - 1) + "😀" + "tail"

        val result = QuoteTextTruncator.truncate(text)

        assertTrue(result.wasCut)
        val body = result.text.removeSuffix(ellipsis)
        assertFalse(Character.isHighSurrogate(body.last()))
    }

    @Test
    fun truncate_trimsTrailingSpacesBeforeEllipsis() {
        val result = QuoteTextTruncator.truncate("word ".repeat(100))

        assertTrue(result.wasCut)
        assertFalse(result.text.contains(" $ellipsis"))
    }
}
