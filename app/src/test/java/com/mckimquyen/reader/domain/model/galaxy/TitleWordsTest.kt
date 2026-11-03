package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleWordsTest {
    @Test fun keepsWordsOfAtLeastFourLettersLowercased() {
        assertEquals(setOf("trump", "zelensky", "putin"), TitleWords.extract("Trump, Zelensky & Putin"))
    }

    @Test fun dropsWordsShorterThanTheMinimum() {
        val words = TitleWords.extract("The EU and UK tax war")
        assertFalse("eu" in words)
        assertFalse("uk" in words)
        assertFalse("tax" in words)
        assertEquals(TitleWords.MIN_WORD_CHARS, 4)
        assertTrue("exactly four letters is kept", "gold" in TitleWords.extract("Gold rush"))
        assertFalse("three letters is dropped", "war" in TitleWords.extract("war"))
    }

    @Test fun dropsStopWordsEvenWhenLongEnough() {
        val words = TitleWords.extract("About this with from their there where")
        assertTrue("stop words must not survive: $words", words.isEmpty())
    }

    @Test fun dropsDigitOnlyTokens() {
        val words = TitleWords.extract("Fined 45000 over 2026 rules")
        assertFalse("45000" in words)
        assertFalse("2026" in words)
        assertTrue("fined" in words)
        assertTrue("rules" in words)
    }

    @Test fun keepsAlphanumericWordsThatAreNotPureDigits() {
        assertTrue("covid19" in TitleWords.extract("Covid19 inquiry"))
    }

    @Test fun splitsOnPunctuationAndApostrophes() {
        val words = TitleWords.extract("Trump's 'energy ceasefire' claim — welcomed")
        assertTrue("energy" in words)
        assertTrue("ceasefire" in words)
        assertTrue("welcomed" in words)
        assertFalse(words.any { it.contains("'") || it.contains("—") })
    }

    @Test fun handlesVietnameseAndOtherUnicodeLetters() {
        val words = TitleWords.extract("Chính phủ công bố gói hỗ trợ mới")
        assertTrue("chính" in words)
        assertTrue("phủ".length < TitleWords.MIN_WORD_CHARS && "phủ" !in words)
        assertTrue("công" !in words || "công".length >= TitleWords.MIN_WORD_CHARS)
        assertTrue("hỗ".length < TitleWords.MIN_WORD_CHARS)
    }

    @Test fun emptyBlankAndSymbolOnlyTitlesGiveNoWords() {
        assertTrue(TitleWords.extract("").isEmpty())
        assertTrue(TitleWords.extract("   ").isEmpty())
        assertTrue(TitleWords.extract("!!! --- ???").isEmpty())
    }

    @Test fun isCaseInsensitiveAndDeduplicated() {
        assertEquals(setOf("burnham"), TitleWords.extract("BURNHAM burnham Burnham"))
    }

    @Test fun dropsQuestionAndAuxiliaryWordsThatLinkUnrelatedHeadlines() {
        // Found in the real archive on a device: "Why does Prince Harry want..." and "Why does Elon Musk want..."
        // were joined by nothing but these two words.
        val words = TitleWords.extract("Why does Prince Harry want to work it out, and who wants this")
        assertFalse("does" in words)
        assertFalse("want" in words)
        assertFalse("wants" in words)
        assertTrue("prince" in words)
        assertTrue("harry" in words)
    }
}
