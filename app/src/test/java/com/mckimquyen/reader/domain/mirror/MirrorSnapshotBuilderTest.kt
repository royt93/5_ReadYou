package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorSnapshotBuilderTest {
    private fun build(text: String, title: String = "T", source: String = "S") =
        MirrorSnapshotBuilder.fromPlainText(title, source, 1L, text, 7L)

    @Test fun splitsParagraphsOnBlankLinesAndTrims() {
        assertEquals(listOf("one", "two", "three"), build("  one \n\n two\n \n\n\nthree  ").paragraphs)
    }

    @Test fun singleNewlinesInsideAParagraphBecomeSpaces() {
        assertEquals(listOf("line one line two"), build("line one\nline two").paragraphs)
    }

    @Test fun emptyAndBlankTextGiveNoParagraphs() {
        assertTrue(build("").paragraphs.isEmpty())
        assertTrue(build(" \n\n \t ").paragraphs.isEmpty())
        assertFalse(build("").truncated)
    }

    @Test fun controlCharactersAreRemovedButTabsAndUnicodeSurvive() {
        val s = build("a\u0000b\u0007c\u001B[31m d\u0085e f 😀 Tiếng Việt")
        assertEquals(1, s.paragraphs.size)
        assertFalse(s.paragraphs[0].any { it.isISOControl() && it != '\t' })
        assertTrue(s.paragraphs[0].contains("Tiếng Việt"))
        assertTrue(s.paragraphs[0].contains("😀"))
    }

    @Test fun unpairedSurrogatesAreDropped() {
        val s = build("ok \uD83D end")
        assertEquals("ok  end".replace("  ", " "), s.paragraphs.single().replace("  ", " "))
    }

    @Test fun longParagraphsAreCutAtTheCharCapAndFlagged() {
        val s = build("x".repeat(MirrorLimits.MAX_PARAGRAPH_CHARS + 500))
        assertEquals(MirrorLimits.MAX_PARAGRAPH_CHARS, s.paragraphs.single().length)
        assertTrue(s.truncated)
    }

    @Test fun aCutNeverSplitsASurrogatePair() {
        val text = "a".repeat(MirrorLimits.MAX_PARAGRAPH_CHARS - 1) + "😀" + "tail"
        val p = build(text).paragraphs.single()
        assertTrue(p.length <= MirrorLimits.MAX_PARAGRAPH_CHARS)
        assertFalse(Character.isHighSurrogate(p.last()))
    }

    @Test fun paragraphCountIsCapped() {
        val s = build((1..MirrorLimits.MAX_PARAGRAPHS + 50).joinToString("\n\n") { "p$it" })
        assertEquals(MirrorLimits.MAX_PARAGRAPHS, s.paragraphs.size)
        assertTrue(s.truncated)
    }

    @Test fun serialisedSizeStaysUnderTheByteCap() {
        val big = (1..MirrorLimits.MAX_PARAGRAPHS).joinToString("\n\n") { "é".repeat(MirrorLimits.MAX_PARAGRAPH_CHARS) }
        val s = build(big)
        assertTrue(s.toJson().toByteArray(Charsets.UTF_8).size <= MirrorLimits.MAX_SNAPSHOT_BYTES)
        assertTrue(s.truncated)
        assertTrue(s.paragraphs.isNotEmpty())
    }

    @Test fun titleAndSourceAreCappedAndCleaned() {
        val s = build("x", title = "t\u0000" + "a".repeat(2_000), source = "s\n" + "b".repeat(500))
        assertTrue(s.title.length <= MirrorSnapshotBuilder.MAX_TITLE_CHARS)
        assertTrue(s.source.length <= MirrorSnapshotBuilder.MAX_SOURCE_CHARS)
        assertFalse(s.title.contains('\u0000'))
        assertFalse(s.source.contains('\n'))
    }

    @Test fun versionAndDateAreKept() {
        val s = build("x")
        assertEquals(7L, s.version)
        assertEquals(1L, s.dateMillis)
    }
}
