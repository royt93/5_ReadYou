package com.mckimquyen.reader.ui.page.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpTokenizerTest {

    @Test
    fun cleanHtml_stripsTagsAndDecodesEntities() {
        val raw = "<p>Hello <b>world</b> &amp; welcome&nbsp;to ReadYou!&#39;s &quot;app&quot;</p>"
        val cleaned = RsvpTokenizer.cleanHtml(raw)
        assertEquals("Hello world & welcome to ReadYou!'s \"app\"", cleaned)
    }

    @Test
    fun calculateOrpIndex_returnsCorrectFocalPoint() {
        // len 0..1 -> 0
        assertEquals(0, RsvpTokenizer.calculateOrpIndex("a"))
        // len 2..5 -> 1
        assertEquals(1, RsvpTokenizer.calculateOrpIndex("to"))
        assertEquals(1, RsvpTokenizer.calculateOrpIndex("read"))
        assertEquals(1, RsvpTokenizer.calculateOrpIndex("hello"))
        // len 6..9 -> 2
        assertEquals(2, RsvpTokenizer.calculateOrpIndex("reader"))
        assertEquals(2, RsvpTokenizer.calculateOrpIndex("android"))
        // len 10..13 -> 3
        assertEquals(3, RsvpTokenizer.calculateOrpIndex("technology"))
        assertEquals(3, RsvpTokenizer.calculateOrpIndex("programming"))
        // len 14+ -> 4
        assertEquals(4, RsvpTokenizer.calculateOrpIndex("internationalization"))
    }

    @Test
    fun calculateExtraDelayMs_addsPunctuationDelays() {
        // Sentence ending punctuation -> +200ms
        val dotDelay = RsvpTokenizer.calculateExtraDelayMs("done.", false)
        assertTrue(dotDelay >= 200L)

        val questionDelay = RsvpTokenizer.calculateExtraDelayMs("why?", false)
        assertTrue(questionDelay >= 200L)

        // Comma -> +80ms
        val commaDelay = RsvpTokenizer.calculateExtraDelayMs("however,", false)
        assertTrue(commaDelay >= 80L)

        // Normal word -> 0ms
        val normalDelay = RsvpTokenizer.calculateExtraDelayMs("normal", false)
        assertEquals(0L, normalDelay)

        // Paragraph end -> +250ms
        val paragraphDelay = RsvpTokenizer.calculateExtraDelayMs("end", true)
        assertTrue(paragraphDelay >= 250L)
    }

    @Test
    fun tokenize_parsesWordsAndSetsOrpParts() {
        val text = "Read speed test."
        val tokens = RsvpTokenizer.tokenize(text)

        assertEquals(3, tokens.size)

        // Word "Read" (length 4 -> ORP index 1 -> 'e')
        val first = tokens[0]
        assertEquals("Read", first.fullWord)
        assertEquals("R", first.prefix)
        assertEquals('e', first.orpChar)
        assertEquals("ad", first.suffix)
        assertEquals(1, first.orpIndex)

        // Word "test." has punctuation delay
        val last = tokens[2]
        assertEquals("test.", last.fullWord)
        assertTrue(last.extraDelayMs >= 200L)
    }

    @Test
    fun tokenize_handlesEmptyAndWhitespace() {
        assertTrue(RsvpTokenizer.tokenize("").isEmpty())
        assertTrue(RsvpTokenizer.tokenize("    \n\t  ").isEmpty())
    }
}
