package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholePhraseMatcherTest {

    @Test
    fun matches_exactWord_withNonLetterBoundaries() {
        assertTrue(WholePhraseMatcher.containsPhrase("Tesla faces big risks ahead", "risks"))
        assertTrue(WholePhraseMatcher.containsPhrase("Warning: storm coming", "Warning"))
        assertTrue(WholePhraseMatcher.containsPhrase("Is this a risk?", "risk"))
        assertTrue(WholePhraseMatcher.containsPhrase("Starlink, under pressure", "Starlink"))
    }

    @Test
    fun rejects_substringMatches() {
        assertFalse(WholePhraseMatcher.containsPhrase("Spread the butter", "but"))
        assertFalse(WholePhraseMatcher.containsPhrase("A brisk autumn morning", "risk"))
        assertFalse(WholePhraseMatcher.containsPhrase("Major disruption hits transport", "disrupt"))
    }

    @Test
    fun matches_multiWordPhrases() {
        assertTrue(WholePhraseMatcher.containsPhrase("Meeting at White House today", "White House"))
        assertFalse(WholePhraseMatcher.containsPhrase("White Household goods on sale", "White House"))
    }

    @Test
    fun handles_emptyOrBlank() {
        assertFalse(WholePhraseMatcher.containsPhrase("Some text", ""))
        assertFalse(WholePhraseMatcher.containsPhrase("Some text", "   "))
        assertFalse(WholePhraseMatcher.containsPhrase("", "phrase"))
    }
}
