package com.mckimquyen.reader.ui.component.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BionicTextFormatterTest {

    private fun ranges(text: String) = BionicTextFormatter.boldRanges(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun empty_andBlank_haveNoRanges() {
        assertTrue(BionicTextFormatter.boldRanges("").isEmpty())
        assertTrue(BionicTextFormatter.boldRanges("   \n\t ").isEmpty())
    }

    @Test
    fun boldsCeil40PercentOfEachWordsLeadingLetters() {
        // lengths 1,2,3,4,5,6,7,8,9,10 -> ceil(0.4*n) = 1,1,2,2,2,3,3,4,4,4
        val expected = listOf(1, 1, 2, 2, 2, 3, 3, 4, 4, 4)
        expected.forEachIndexed { index, bolded ->
            val word = "a".repeat(index + 1)
            assertEquals("word of ${index + 1} letters", bolded, ranges(word).single().length)
        }
    }

    @Test
    fun everyWordGetsItsOwnRange_andWhitespaceIsNeverBolded() {
        val text = "Bionic   reading\nhelps"
        assertEquals(listOf("Bio", "rea", "he"), ranges(text))
        assertTrue(ranges(text).none { it.isBlank() })
    }

    @Test
    fun punctuationIsNotBolded_andNotCountedAsLetters() {
        assertEquals(listOf("He", "wo"), ranges("Hello, world!"))
        assertTrue(BionicTextFormatter.boldRanges("... --- !!!").isEmpty())
    }

    @Test
    fun apostropheAndHyphenKeepAWordTogether() {
        // "don't" (5 chars) and "well-known" (10 chars) are each ONE word: 2 and 4 leading characters bold.
        assertEquals(listOf("do", "well"), ranges("don't well-known"))
    }

    @Test
    fun vietnameseDiacriticsCountAsOneLetterEach_precomposedAndDecomposed() {
        val precomposed = "Nguyễn" // 6 letters -> bold 3 = "Ngu"
        assertEquals("Ngu", ranges(precomposed).single())

        val decomposed = "Nguyễn" // 'e' + two combining marks, still 6 user-visible letters
        val bolded = ranges(decomposed).single()
        assertTrue("combining marks must not be split from their base letter", bolded == "Ngu" || bolded.startsWith("Ngu"))
        assertTrue("never end the bold span in the middle of a base+marks cluster", !bolded.endsWith("̂") && !bolded.endsWith("̃"))
    }

    @Test
    fun surrogatePairsAreNeverSplit() {
        val text = "😀😀😀 abc" // three emoji (6 UTF-16 units), then a word
        BionicTextFormatter.boldRanges(text).forEach { range ->
            val end = range.last + 1
            assertTrue("range end $end splits a surrogate pair", end >= text.length || !Character.isLowSurrogate(text[end]))
            assertTrue(!Character.isLowSurrogate(text[range.first]))
        }
    }

    @Test
    fun cjkTextWithoutSpacesIsTreatedAsOneWord_andBoldsItsLeadingCharacters() {
        val text = "今日はいい天気ですね" // 10 characters, no spaces
        assertEquals(4, ranges(text).single().length)
    }

    @Test
    fun digitsAreWords_butUrlsAreLeftAlone() {
        assertEquals(listOf("12"), ranges("12345").map { it })
        val text = "see https://example.com/a-b?x=1 now"
        val bolded = ranges(text)
        assertTrue("url must not be bolded: $bolded", bolded.none { it.contains("http") || it.contains("example") })
        assertEquals(listOf("se", "no"), bolded)
    }

    @Test
    fun everyPartOfAUrlOrAddressIsLeftAlone_includingQueryStringAndFragment() {
        for (token in listOf(
            "https://example.com/a-b?x=1&y=22#frag",
            "http://example.com",
            "www.example.org/page",
            "someone@example.com",
            "example.com/path/file.html",
        )) {
            val text = "ok $token ok"
            val bolded = ranges(text)
            assertEquals("only the two surrounding words are bolded for $token, got $bolded", listOf("o", "o"), bolded)
        }
    }

    @Test
    fun aSentenceEndingDotIsNotMistakenForAUrl() {
        assertEquals(listOf("en", "Ne"), ranges("end. Next"))
        assertEquals(listOf("pl", "wo", "he"), ranges("plain words here"))
    }

    @Test
    fun rangesAreOrderedNonOverlappingAndInsideTheText() {
        val text = "The quick brown fox jumps over the lazy dog 12345 times, naturally!"
        val all = BionicTextFormatter.boldRanges(text)
        var previousEnd = -1
        for (r in all) {
            assertTrue(r.first > previousEnd)
            assertTrue(r.first >= 0 && r.last < text.length)
            previousEnd = r.last
        }
    }

    @Test
    fun veryLongInput_isLinear_andFastEnough() {
        val text = ("Lorem ipsum dolor sit amet consectetur adipiscing elit " * 4000)
        BionicTextFormatter.boldRanges(text) // warm up
        val start = System.nanoTime()
        val result = BionicTextFormatter.boldRanges(text)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(8 * 4000, result.size)
        assertTrue("formatting ${text.length} chars took ${ms}ms (spec < 50ms for a normal article)", ms < 500)
    }

    private operator fun String.times(n: Int) = repeat(n)
}
