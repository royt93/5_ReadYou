package com.mckimquyen.reader.domain.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotNormalizerTest {

    @Test
    fun toPlainText_stripsTagsScriptsAndStyles() {
        val html = "<p>Hello <b>world</b></p><script>alert(1)</script><style>p{color:red}</style>"
        assertEquals("Hello world", SnapshotNormalizer.toPlainText(html))
    }

    @Test
    fun toPlainText_collapsesWhitespaceAndDecodesBasicEntities() {
        assertEquals("a & b < c", SnapshotNormalizer.toPlainText("a&nbsp;&amp;\n\n  b &lt; c"))
    }

    @Test
    fun toPlainText_emptyAndBlank() {
        assertEquals("", SnapshotNormalizer.toPlainText(""))
        assertEquals("", SnapshotNormalizer.toPlainText("  <p> </p> "))
    }

    @Test
    fun toPlainText_capsLengthAtMaxChars() {
        val long = "x".repeat(SnapshotNormalizer.MAX_SNAPSHOT_CHARS + 500)
        assertEquals(SnapshotNormalizer.MAX_SNAPSHOT_CHARS, SnapshotNormalizer.toPlainText(long).length)
    }

    @Test
    fun toPlainText_doesNotSplitSurrogatePairAtCap() {
        val text = "a".repeat(SnapshotNormalizer.MAX_SNAPSHOT_CHARS - 1) + "😀😀"
        val out = SnapshotNormalizer.toPlainText(text)
        assertTrue(out.length <= SnapshotNormalizer.MAX_SNAPSHOT_CHARS)
        assertTrue(!Character.isHighSurrogate(out.last()))
    }

    @Test
    fun hash_ignoresWhitespaceTagsAndCase() {
        val a = SnapshotNormalizer.hash(SnapshotNormalizer.toPlainText("<p>Breaking  News</p>"))
        val b = SnapshotNormalizer.hash(SnapshotNormalizer.toPlainText("breaking news"))
        assertEquals(a, b)
    }

    @Test
    fun hash_changesWhenWordsChange() {
        assertNotEquals(SnapshotNormalizer.hash("minister resigns"), SnapshotNormalizer.hash("minister stays"))
    }

    @Test
    fun hash_isSha256Hex() {
        assertEquals(64, SnapshotNormalizer.hash("x").length)
        assertEquals("2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881", SnapshotNormalizer.hash("x"))
    }
}
