package com.mckimquyen.reader.infrastructure.ai.search

import com.mckimquyen.reader.domain.model.galaxy.TitleWords
import org.junit.Assert.assertTrue
import org.junit.Test

class StopWordsTest {
    @Test fun everySearchEngineStopWordIsAlsoIgnoredInGalaxyTitles() {
        // The two lists live in different layers; this keeps the galaxy from linking on a word the app already
        // treats as meaningless elsewhere.
        val leaked = SemanticSearchEngine.STOP_WORDS.filter { word ->
            word.length >= TitleWords.MIN_WORD_CHARS && word in TitleWords.extract("$word zzzz")
        }
        assertTrue("stop words that still reach a title: $leaked", leaked.isEmpty())
    }

    @Test fun theSharedListIsNotEmpty() {
        assertTrue(SemanticSearchEngine.STOP_WORDS.size >= 40)
    }
}
