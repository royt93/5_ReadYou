package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPhraseExtractorTest {

    @Test
    fun extracts_properNounRuns_upToThreeWords() {
        val phrases = TopicPhraseExtractor.extract("Sam Altman discusses AI chip strategy with TSMC", "BBC News")
        assertTrue(phrases.contains("Sam Altman"))
        assertTrue(phrases.contains("TSMC"))
    }

    @Test
    fun drops_feedName_andPhrasesContainedInFeedName() {
        val phrases = TopicPhraseExtractor.extract("BBC follows police on patrol in Liverpool", "BBC News")
        assertFalse(phrases.contains("BBC"))
        assertFalse(phrases.contains("BBC News"))
        assertTrue(phrases.contains("Liverpool"))
    }

    @Test
    fun drops_sentenceInitialCommonWords_andDays() {
        val phrases = TopicPhraseExtractor.extract("Monday brings heavy rain as Starlink recovers", "Reuters")
        assertFalse(phrases.contains("Monday"))
        assertTrue(phrases.contains("Starlink"))
    }

    @Test
    fun capsAtThreePhrases_longestFirst() {
        val phrases = TopicPhraseExtractor.extract(
            "European Space Agency launches Ariane rocket alongside NASA and SpaceX",
            "Wired",
        )
        assertTrue(phrases.size <= TopicPhraseExtractor.MAX_PHRASES)
        assertEquals("European Space Agency", phrases.first())
    }

    @Test
    fun dropsPhrasesShorterThanMinimumLength() {
        val phrases = TopicPhraseExtractor.extract("US and UK meet on trade", "CNN")
        assertTrue(phrases.none { it.length < TopicPhraseExtractor.MIN_PHRASE_LENGTH })
    }

    @Test
    fun returnsEmpty_forLowercaseOrBlank() {
        assertTrue(TopicPhraseExtractor.extract("all lowercase headline here", "Feed").isEmpty())
        assertTrue(TopicPhraseExtractor.extract("", "Feed").isEmpty())
    }

    @Test
    fun possessiveEndsARun_soTwoNamesAreNeverMergedIntoOnePhrase() {
        val phrases = TopicPhraseExtractor.extract(
            "Musk says SpaceX restricted Ukraine's Starlink connection as service restored",
            "BBC News",
        )
        assertTrue(phrases.contains("Starlink"))
        assertTrue(phrases.contains("Ukraine"))
        assertFalse(phrases.contains("Ukraine Starlink"))
    }

    @Test
    fun deduplicatesCaseInsensitively() {
        val phrases = TopicPhraseExtractor.extract("Starlink and STARLINK clash over Starlink", "Feed")
        assertEquals(phrases.map { it.lowercase() }.distinct(), phrases.map { it.lowercase() })
    }
}
