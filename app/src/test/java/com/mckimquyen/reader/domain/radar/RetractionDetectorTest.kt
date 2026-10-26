package com.mckimquyen.reader.domain.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetractionDetectorTest {

    private val original = RetractionDetector.snapshotOf("Minister resigns", "<p>The minister resigned on Monday.</p>")

    @Test
    fun noPreviousSnapshot_isNew() {
        assertEquals(RetractionVerdict.NEW, RetractionDetector.evaluate(null, original))
    }

    @Test
    fun sameTextWithDifferentMarkupAndCase_isUnchanged() {
        val incoming = RetractionDetector.snapshotOf("MINISTER  resigns", "<div>The   minister resigned on Monday.</div><script>x()</script>")
        assertEquals(RetractionVerdict.UNCHANGED, RetractionDetector.evaluate(original.hashes(), incoming))
    }

    @Test
    fun changedTitle_isEdited() {
        val incoming = RetractionDetector.snapshotOf("Minister stays", "<p>The minister resigned on Monday.</p>")
        assertEquals(RetractionVerdict.EDITED, RetractionDetector.evaluate(original.hashes(), incoming))
    }

    @Test
    fun changedBody_isEdited() {
        val incoming = RetractionDetector.snapshotOf("Minister resigns", "<p>The minister resigned on Tuesday.</p>")
        assertEquals(RetractionVerdict.EDITED, RetractionDetector.evaluate(original.hashes(), incoming))
    }

    @Test
    fun emptyIncomingBody_doesNotCountAsAnEdit() {
        // Feeds sometimes drop the body on a later fetch; that is a feed quirk, not a retraction.
        val incoming = RetractionDetector.snapshotOf("Minister resigns", "")
        assertEquals(RetractionVerdict.UNCHANGED, RetractionDetector.evaluate(original.hashes(), incoming))
    }

    @Test
    fun blankIncomingTitle_doesNotCountAsAnEdit() {
        val incoming = RetractionDetector.snapshotOf("  ", "<p>The minister resigned on Monday.</p>")
        assertEquals(RetractionVerdict.UNCHANGED, RetractionDetector.evaluate(original.hashes(), incoming))
    }

    @Test
    fun snapshotCarriesPlainTextAndHashes() {
        assertEquals("The minister resigned on Monday.", original.contentText)
        assertEquals(64, original.titleHash.length)
        assertTrue(original.contentHash != original.titleHash)
    }
}
