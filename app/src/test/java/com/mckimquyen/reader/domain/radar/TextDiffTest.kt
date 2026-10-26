package com.mckimquyen.reader.domain.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {

    private fun render(parts: List<DiffPart>) = parts.joinToString("|") { "${it.type.name[0]}:${it.text}" }

    @Test
    fun identicalTextIsAllSame() {
        assertEquals("S:a b c", render(TextDiff.wordDiff("a b c", "a b c")))
    }

    @Test
    fun replacedWordIsRemovedThenAdded() {
        assertEquals("S:minister|R:resigns|A:stays|S:today", render(TextDiff.wordDiff("minister resigns today", "minister stays today")))
    }

    @Test
    fun pureInsertionAndDeletion() {
        assertEquals("S:a|A:b|S:c", render(TextDiff.wordDiff("a c", "a b c")))
        assertEquals("S:a|R:b|S:c", render(TextDiff.wordDiff("a b c", "a c")))
    }

    @Test
    fun emptySides() {
        assertEquals("A:x y", render(TextDiff.wordDiff("", "x y")))
        assertEquals("R:x y", render(TextDiff.wordDiff("x y", "")))
        assertTrue(TextDiff.wordDiff("", "").isEmpty())
    }

    @Test
    fun unicodeWordsCompareExactly() {
        assertEquals("S:Việt|R:Nam|A:Hà", render(TextDiff.wordDiff("Việt Nam", "Việt Hà")))
    }

    @Test
    fun reconstructsBothSides() {
        val old = "the quick brown fox jumps over the lazy dog"
        val new = "the quick red fox leaps over a lazy dog today"
        val parts = TextDiff.wordDiff(old, new)
        assertEquals(old, parts.filter { it.type != DiffType.ADDED }.joinToString(" ") { it.text })
        assertEquals(new, parts.filter { it.type != DiffType.REMOVED }.joinToString(" ") { it.text })
    }

    @Test
    fun hugeInputFallsBackWithoutBlowingUp() {
        val old = (1..5000).joinToString(" ") { "o$it" }
        val new = (1..5000).joinToString(" ") { "n$it" }
        val start = System.nanoTime()
        val parts = TextDiff.wordDiff(old, new)
        assertTrue("took too long", (System.nanoTime() - start) / 1_000_000 < 2000)
        assertEquals(old, parts.filter { it.type != DiffType.ADDED }.joinToString(" ") { it.text })
        assertEquals(new, parts.filter { it.type != DiffType.REMOVED }.joinToString(" ") { it.text })
    }
}
