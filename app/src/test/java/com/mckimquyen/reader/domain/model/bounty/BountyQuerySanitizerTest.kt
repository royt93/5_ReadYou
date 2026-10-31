package com.mckimquyen.reader.domain.model.bounty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BountyQuerySanitizerTest {

    @Test
    fun sanitize_collapsesWhitespaceAndTrimsEdgePunctuation() {
        assertEquals("DeepSeek-R1", BountyQuerySanitizer.sanitize("  \"DeepSeek-R1!\"  "))
        assertEquals("Sam Altman", BountyQuerySanitizer.sanitize(" (Sam Altman) "))
        assertEquals("Agent-007", BountyQuerySanitizer.sanitize("...Agent-007???"))
    }

    @Test
    fun sanitize_usesFirstSentenceOnly() {
        assertEquals("Company Alpha", BountyQuerySanitizer.sanitize("Company Alpha. Second sentence here."))
        assertEquals("First clause", BountyQuerySanitizer.sanitize("First clause; second clause follows"))
        assertEquals("Single line", BountyQuerySanitizer.sanitize("Single line\nSecond line"))
    }

    @Test
    fun sanitize_capsAt100CharactersWithoutBreakingSurrogates() {
        val longText = "A".repeat(150)
        val sanitized = requireNotNull(BountyQuerySanitizer.sanitize(longText))
        assertEquals(BountyQuerySanitizer.MAX_QUERY_LENGTH, sanitized.length)
        assertEquals(100, sanitized.length)
    }

    @Test
    fun sanitize_neverSplitsSurrogatePairAtTheCap() {
        // 99 ký tự + emoji (2 UTF-16 unit) => ranh giới 100 rơi giữa cặp surrogate.
        val text = "A".repeat(99) + "😀" + "tail"
        val sanitized = requireNotNull(BountyQuerySanitizer.sanitize(text))
        assertEquals(99, sanitized.length)
        assertEquals(false, Character.isHighSurrogate(sanitized.last()))
    }

    @Test
    fun sanitize_rejectsBlankOrTooShortText() {
        assertNull(BountyQuerySanitizer.sanitize(null))
        assertNull(BountyQuerySanitizer.sanitize(""))
        assertNull(BountyQuerySanitizer.sanitize("   \n\t  "))
        assertNull(BountyQuerySanitizer.sanitize("x"))
        assertNull(BountyQuerySanitizer.sanitize(" \"!\" "))
    }

    @Test
    fun sanitize_preservesInnerHyphensAndDiacritics() {
        assertEquals("Đại học Bách Khoa", BountyQuerySanitizer.sanitize("Đại học Bách Khoa"))
        assertEquals("COVID-19 vaccine", BountyQuerySanitizer.sanitize("COVID-19 vaccine."))
    }

    @Test
    fun escapeLikeLiteral_escapesBackslashPercentAndUnderscore() {
        assertEquals("A\\%B\\_C\\\\D", BountyQuerySanitizer.escapeLikeLiteral("A%B_C\\D"))
        assertEquals("normal text", BountyQuerySanitizer.escapeLikeLiteral("normal text"))
        assertEquals("100\\%", BountyQuerySanitizer.escapeLikeLiteral("100%"))
    }
}
