package com.mckimquyen.reader.domain.headline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlineAnalyzerTest {

    private fun analyze(title: String) = requireNotNull(HeadlineAnalyzer.analyze(title))

    @Test
    fun blankOrUnsupportedTitlesReturnNull() {
        assertNull(HeadlineAnalyzer.analyze("  \n "))
        assertNull(HeadlineAnalyzer.analyze("東京の鉄道ニュース"))
        assertNull(HeadlineAnalyzer.analyze("12345 !!!"))
    }

    @Test
    fun scoreIsDeterministicAndBounded() {
        val title = "SHOCKING: You won't believe what happened next!!!"
        assertEquals(analyze(title), analyze(title))
        assertTrue(analyze(title).clickbaitScore in 0..100)
    }

    @Test
    fun ordinaryBbcHeadlineDoesNotCrossClickbaitThreshold() {
        val signal = analyze("Chancellor to cut tariffs on some staple goods to offer cost of living help")
        assertTrue("score was ${signal.clickbaitScore}", signal.clickbaitScore <= HeadlineAnalyzer.CLICKBAIT_THRESHOLD)
    }

    @Test
    fun baitPhraseAndRepeatedPunctuationRaiseClickbaitScore() {
        val plain = analyze("Council approves new tram route")
        val bait = analyze("Shocking! Council reveals the truth about new tram route!!!")
        assertTrue("plain=${plain.clickbaitScore}, bait=${bait.clickbaitScore}", bait.clickbaitScore > plain.clickbaitScore)
    }

    @Test
    fun uppercaseProportionRaisesScore() {
        val plain = analyze("Council approves a new transport route")
        val uppercase = analyze("COUNCIL APPROVES A NEW TRANSPORT ROUTE")
        assertTrue(uppercase.clickbaitScore > plain.clickbaitScore)
    }

    @Test
    fun numberedListAndIntensifierRaiseScore() {
        val plain = analyze("Ways to improve sleep during winter")
        val list = analyze("10 unbelievable ways to improve sleep during winter")
        assertTrue(list.clickbaitScore > plain.clickbaitScore)
    }

    @Test
    fun questionWithCuriosityGapRaisesScore() {
        val plain = analyze("The council released its budget report today")
        val question = analyze("What the council does not want you to know about its budget")
        assertTrue(question.clickbaitScore > plain.clickbaitScore)
    }

    @Test
    fun scoreClampsAtOneHundredAndTitleWorkIsBounded() {
        val title = ("SHOCKING unbelievable secret truth!!! ").repeat(100)
        assertTrue(analyze(title).clickbaitScore in 0..100)
        assertEquals(analyze(title.take(240)), analyze(title))
    }

    @Test
    fun clickbaitThresholdIsStrictlyGreaterThanSeventy() {
        assertEquals(false, HeadlineAnalyzer.shouldShowClickbait(70))
        assertEquals(true, HeadlineAnalyzer.shouldShowClickbait(71))
        assertEquals(false, HeadlineAnalyzer.shouldShowClickbait(0))
        assertEquals(true, HeadlineAnalyzer.shouldShowClickbait(100))
    }

    @Test
    fun allFiveSupportedLanguagesAreDetected() {
        assertEquals(HeadlineLanguage.ENGLISH, analyze("Shocking: the truth revealed today").language)
        assertEquals(HeadlineLanguage.VIETNAMESE, analyze("Sự thật chấn động: điều gì vừa xảy ra?").language)
        assertEquals(HeadlineLanguage.SPANISH, analyze("La verdad impactante que nadie esperaba").language)
        assertEquals(HeadlineLanguage.FRENCH, analyze("La vérité incroyable que personne n'attendait").language)
        assertEquals(HeadlineLanguage.GERMAN, analyze("Die unglaubliche Wahrheit, die niemand erwartet hat").language)
    }

    @Test
    fun titleOverLimitStillDetectsAndScoresOnlyTheBoundedPrefix() {
        val prefix = "Shocking news: the truth is revealed today"
        val suffix = " ordinary words".repeat(50)
        assertEquals(analyze(prefix), analyze(prefix + suffix))
    }

    @Test
    fun vietnameseHeadlinesUseVietnameseLexicon() {
        val plain = analyze("Hội đồng thành phố phê duyệt tuyến xe điện mới")
        val bait = analyze("Sự thật chấn động: bạn sẽ không tin điều hội đồng vừa làm!!!")
        assertEquals(HeadlineLanguage.VIETNAMESE, bait.language)
        assertTrue(bait.clickbaitScore > plain.clickbaitScore)
    }

    @Test
    fun positiveAndNegativeToneRequireLexicalEvidence() {
        assertEquals(HeadlineTone.POSITIVE, analyze("Fans celebrate as local team wins historic final").tone)
        assertEquals(HeadlineTone.NEGATIVE, analyze("Deadly storm destroys homes as families mourn").tone)
    }

    @Test
    fun tieAndWeakToneEvidenceAreUnclear() {
        assertEquals(HeadlineTone.UNCLEAR, analyze("Council meets on Tuesday").tone)
        assertEquals(HeadlineTone.UNCLEAR, analyze("Fans celebrate as rivals suffer a defeat").tone)
    }

    @Test
    fun tragicFactualHeadlineWithoutEmotionWordsIsNotAutomaticallyNegative() {
        assertEquals(HeadlineTone.UNCLEAR, analyze("Two men killed by falling trees as Storm Boelo hits UK").tone)
    }

    @Test
    fun repeatedSignalsDoNotOverflowScore() {
        val title = "SHOCKING!!! YOU WILL NOT BELIEVE THE TRUTH??? ".repeat(80)
        assertTrue(analyze(title).clickbaitScore in 0..100)
        assertEquals(analyze(title.take(240)), analyze(title))
    }

    @Test
    fun punctuationAndUnicodeRemainDeterministic() {
        val title = "¿Qué pasó? La verdad increíble está aquí… ¡No te lo pierdas!"
        assertEquals(analyze(title), analyze(title))
        assertNotEquals(HeadlineLanguage.UNSUPPORTED, analyze(title).language)
    }
}
