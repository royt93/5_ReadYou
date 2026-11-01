package com.mckimquyen.reader.domain.headline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSignalAnalyzerTest {

    private val promotionalEnglish = """
        <p>Introducing the revolutionary, game-changing platform that is the best ever solution on the market!!!</p>
        <p>This world-class, groundbreaking product is guaranteed to deliver unparalleled results, seamlessly and exclusively for you.</p>
        <p>Our unbeatable breakthrough is a must-have for every company that wants to disrupt its industry.</p>
    """.trimIndent()

    private val factualEnglish = """
        <p>The council approved a new tram route on Tuesday after a vote that ended seven to four.</p>
        <p>Construction is expected to begin next spring and will take about two years, according to the transport department.</p>
        <p>Residents will be consulted about the timetable and the location of the stops before work starts.</p>
    """.trimIndent()

    private val promotionalVietnamese = """
        <p>Giới thiệu giải pháp đột phá, cách mạng, hàng đầu thế giới và số 1 trên thị trường!!!</p>
        <p>Sản phẩm vượt trội, hoàn hảo, chưa từng có này cam kết 100% hiệu quả, độc quyền dành cho bạn.</p>
    """.trimIndent()

    private fun analyze(text: String) = requireNotNull(ArticleSignalAnalyzer.analyze(text)) { "expected a result" }

    @Test
    fun promotionalBody_scoresHighAndRaisesTheWarning() {
        val result = analyze(promotionalEnglish)

        assertTrue("hype=${result.hypeScore}", result.hypeScore >= ArticleSignalAnalyzer.HYPE_WARNING_THRESHOLD)
        assertTrue(result.hasHypeWarning)
        assertTrue(result.isNoteworthy)
        assertEquals(HeadlineLanguage.ENGLISH, result.language)
    }

    @Test
    fun factualBody_staysBelowTheNoteworthyThreshold_andIsNotNoteworthy() {
        val result = analyze(factualEnglish)

        assertTrue("hype=${result.hypeScore}", result.hypeScore < ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD)
        assertEquals(HeadlineTone.UNCLEAR, result.tone)
        assertFalse(result.isNoteworthy)
        assertFalse(result.hasHypeWarning)
    }

    @Test
    fun vietnamesePromotionalBody_isSupportedAndScoresHigh() {
        val result = analyze(promotionalVietnamese)

        assertEquals(HeadlineLanguage.VIETNAMESE, result.language)
        assertTrue("hype=${result.hypeScore}", result.hypeScore >= ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD)
    }

    @Test
    fun scoreIsAlwaysWithinZeroAndHundred_andDenseShortCopyReachesTheCeiling() {
        // Many short, cue-dense paragraphs: the density formula can exceed 100 per paragraph and must be clamped.
        val denseParagraph = "<p>This is the best ever and guaranteed revolutionary game-changing unparalleled deal!!!</p>"
        val dense = analyze(denseParagraph.repeat(60))
        assertTrue("dense=${dense.hypeScore}", dense.hypeScore in 0..100)
        assertEquals("dense short copy should hit the ceiling", 100, dense.hypeScore)

        // One enormous paragraph dilutes the same cues; it must stay in range too, and far lower.
        val diluted = analyze("<p>" + "This is the revolutionary game-changing product and it is the best ever and guaranteed to be unparalleled!!! ".repeat(200) + "</p>")
        assertTrue("diluted=${diluted.hypeScore}", diluted.hypeScore in 0..100)
        assertTrue(diluted.hypeScore < dense.hypeScore)
    }

    @Test
    fun longFactualTextDoesNotAccumulateHypeJustFromLength() {
        val oneCue = "<p>The firm called its product revolutionary in a statement.</p>"
        val filler = "<p>The committee met on Monday and discussed the budget for the coming year in detail.</p>"
        val short = analyze(oneCue + filler)
        val long = analyze(oneCue + filler.repeat(120))

        assertTrue("short=${short.hypeScore}, long=${long.hypeScore}", long.hypeScore <= short.hypeScore)
    }

    @Test
    fun hypeIsIndependentFromTone() {
        val positive = analyze(promotionalEnglish + "<p>The team celebrates a record victory and success.</p>")
        val negative = analyze(promotionalEnglish + "<p>Yet the disaster, tragedy and crisis caused deadly losses and grief.</p>")

        assertEquals(HeadlineTone.POSITIVE, positive.tone)
        assertEquals(HeadlineTone.NEGATIVE, negative.tone)
        assertTrue(positive.hasHypeWarning)
        assertTrue(negative.hasHypeWarning)
    }

    @Test
    fun toneUnclearWhenEvidenceIsInsufficientEvenForPromotionalText() {
        val result = analyze(promotionalEnglish)

        assertEquals(HeadlineTone.UNCLEAR, result.tone)
    }

    @Test
    fun flaggedPassages_areCappedSortedAndBounded() {
        val paragraph = "<p>This is the revolutionary game-changing world-class groundbreaking product and it is the best ever and guaranteed to be unparalleled!!! %s</p>"
        val body = (1..12).joinToString("") { paragraph.format("and the results of the study are in the report ".repeat(it)) }

        val passages = analyze(body).flaggedPassages

        assertTrue(passages.size <= ArticleSignalAnalyzer.MAX_FLAGGED_PASSAGES)
        assertTrue(passages.isNotEmpty())
        assertTrue(passages.all { it.text.length <= ArticleSignalAnalyzer.MAX_PASSAGE_CHARS })
        assertTrue(passages.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(passages.all { it.cues.isNotEmpty() })
    }

    @Test
    fun passageTruncation_neverSplitsASurrogatePair() {
        val emoji = "😀"
        val lead = "This is the revolutionary game-changing groundbreaking product and it is the best ever and guaranteed to be unparalleled!!! "
        val padding = "x".repeat(ArticleSignalAnalyzer.MAX_PASSAGE_CHARS - lead.length - 1)
        val body = "<p>$lead$padding$emoji tail of the story</p>"

        val passage = analyze(body).flaggedPassages.single()

        assertTrue(passage.text.length <= ArticleSignalAnalyzer.MAX_PASSAGE_CHARS)
        assertFalse(Character.isHighSurrogate(passage.text.last()))
    }

    @Test
    fun noParagraphAboveThreshold_meansNoFlaggedPassages() {
        assertTrue(analyze(factualEnglish).flaggedPassages.isEmpty())
    }

    @Test
    fun htmlTagsAndEntitiesAreStrippedBeforeAnalysis() {
        val html = "<div><script>revolutionary()</script><p>The council approved the route &amp; the budget.</p></div>"

        val result = ArticleSignalAnalyzer.analyze(html + factualEnglish)

        assertNotNull(result)
        assertFalse(result!!.isNoteworthy)
    }

    @Test
    fun blankOrTooShortOrUnsupportedBodiesReturnNull() {
        assertNull(ArticleSignalAnalyzer.analyze(""))
        assertNull(ArticleSignalAnalyzer.analyze("   \n  "))
        assertNull(ArticleSignalAnalyzer.analyze("<p>Short.</p>"))
        assertNull(ArticleSignalAnalyzer.analyze("<p>東京の鉄道ニュースです。今日は電車が遅れています。東京の鉄道ニュースです。</p>"))
    }

    @Test
    fun bodyLongerThanCapIsBoundedAndStillAnalyzed() {
        val huge = "<p>" + "The council approved the route after a long debate. ".repeat(2_000) + "</p>"
        assertTrue(huge.length > ArticleSignalAnalyzer.MAX_ANALYZED_BODY_CHARS)

        val result = analyze(huge)

        assertTrue(result.hypeScore in 0..100)
        assertTrue(result.flaggedPassages.all { it.text.length <= ArticleSignalAnalyzer.MAX_PASSAGE_CHARS })
    }

    @Test
    fun quotationAboutHypeIsOnlyAWordingCue_notAFalsehoodClaim() {
        val body = "<p>Critics said the term 'game-changing' is overused in marketing.</p>" + factualEnglish

        val result = analyze(body)

        assertTrue(result.flaggedPassages.all { it.cues.all { cue -> cue in HypeCueCategory.values() } })
        assertFalse(result.hasHypeWarning)
    }

    @Test
    fun analysisIsDeterministic() {
        assertEquals(analyze(promotionalEnglish), analyze(promotionalEnglish))
    }

    // Regression: real BBC items scored 100 from substring hits ("disrupt UK democracy", "number one" in tennis).
    @Test
    fun shortFactualNewsItems_neverReadAsHype() {
        val items = listOf(
            "He says voters were not \"sufficiently\" informed about how foreign actors sought to disrupt UK democracy and the vote.",
            "British number one Katie Boulter loses to Diana Shnaider in the Wuhan Open first round after a long match.",
            "TFG London has apologised to customers for disruption after it identified a \"cybersecurity incident\" on Sunday.",
        )
        items.forEach { text ->
            val result = ArticleSignalAnalyzer.analyze("<p>$text</p>")
            assertTrue("'$text' -> ${result?.hypeScore}", result == null || result.hypeScore < ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD)
        }
    }

    @Test
    fun phraseMatchingRespectsWordBoundaries() {
        val inside = analyze("<p>The disruption to services and the discorevolutionary spelling are unrelated to any marketing claim here.</p>")
        val whole = analyze(promotionalEnglish)
        assertTrue(inside.flaggedPassages.isEmpty())
        assertTrue(whole.flaggedPassages.isNotEmpty())
    }

    @Test
    fun oneCueInAShortItemCannotReachTheCeiling() {
        val result = ArticleSignalAnalyzer.analyze("<p>The firm called its new product revolutionary in a short statement today.</p>")

        assertTrue("hype=${result?.hypeScore}", result == null || result.hypeScore < ArticleSignalAnalyzer.HYPE_WARNING_THRESHOLD)
    }

    // Regression from the 176-article real-data probe: a proper noun containing "Revolutionary" read as hype.
    @Test
    fun properNounContainingACueWord_isNotEnoughEvidence() {
        val result = ArticleSignalAnalyzer.analyze(
            "<p>The prime minister says Iran's Islamic Revolutionary Guard \"almost certainly\" directed the incident.</p>",
        )

        assertTrue("hype=${result?.hypeScore}", result == null || result.hypeScore < ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD)
        assertFalse(result?.isNoteworthy ?: false)
    }

    @Test
    fun singleCueCannotBeNoteworthyButCorroboratedCuesCan() {
        val single = analyze("<p>The firm called its new product revolutionary in a statement to the press today, ending a long week.</p>")
        val corroborated = analyze("<p>This revolutionary and game-changing product is guaranteed to be the best ever!!!</p>")

        assertTrue(single.hypeScore < ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD)
        assertTrue(corroborated.hypeScore >= ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD)
    }
}
