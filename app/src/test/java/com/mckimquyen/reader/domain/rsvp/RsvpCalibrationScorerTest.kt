package com.mckimquyen.reader.domain.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpCalibrationScorerTest {

    private fun round(wpm: Int, correct: Int) = RoundResult(wpm = wpm, correct = correct, total = 2)

    @Test
    fun emptyResults_returnsFallbackWpm_defaultScale_lowConfidence() {
        val s = RsvpCalibrationScorer.score(emptyList())
        assertEquals(RsvpCalibrationPlan.FALLBACK_WPM, s.wpm)
        assertEquals(RsvpCalibrationPlan.DEFAULT_DELAY_SCALE, s.punctuationDelayScale, 0.0001f)
        assertEquals(Confidence.LOW, s.confidence)
        assertTrue(s.passedRounds.isEmpty())
    }

    @Test
    fun noRoundPassed_returnsFallbackWpm_andScale1_5_lowConfidence() {
        val s = RsvpCalibrationScorer.score(listOf(round(300, 0), round(450, 1), round(600, 0)))
        assertEquals(RsvpCalibrationPlan.FALLBACK_WPM, s.wpm)
        assertEquals(1.5f, s.punctuationDelayScale, 0.0001f) // 1 of 6 correct
        assertEquals(Confidence.LOW, s.confidence)
    }

    @Test
    fun allRoundsPerfect_returnsHighestWpm_scale0_85_mediumConfidence() {
        val s = RsvpCalibrationScorer.score(listOf(round(300, 2), round(450, 2), round(600, 2)))
        assertEquals(600, s.wpm)
        assertEquals(0.85f, s.punctuationDelayScale, 0.0001f)
        assertEquals(Confidence.MEDIUM, s.confidence)
        assertEquals(listOf(300, 450, 600), s.passedRounds)
    }

    @Test
    fun passed300And450_missedOneAt600_returns450_scale1_0_mediumConfidence() {
        val s = RsvpCalibrationScorer.score(listOf(round(300, 2), round(450, 2), round(600, 1)))
        assertEquals(450, s.wpm)
        assertEquals(1.0f, s.punctuationDelayScale, 0.0001f) // 5 of 6 = 83%, at least 2/3
        assertEquals(Confidence.MEDIUM, s.confidence)
    }

    @Test
    fun passedOnly300_returns300_scale1_25_mediumConfidence() {
        val s = RsvpCalibrationScorer.score(listOf(round(300, 2), round(450, 1), round(600, 0)))
        assertEquals(300, s.wpm)
        assertEquals(1.25f, s.punctuationDelayScale, 0.0001f) // 3 of 6 = 50%
        assertEquals(Confidence.MEDIUM, s.confidence)
    }

    @Test
    fun luckyFastRound_afterFailingSlowerOnes_isLowConfidence() {
        val s = RsvpCalibrationScorer.score(listOf(round(300, 1), round(450, 0), round(600, 2)))
        assertEquals(600, s.wpm)
        assertEquals(Confidence.LOW, s.confidence)
    }

    @Test
    fun passedOnly450_whileFailing300_isLowConfidence() {
        val s = RsvpCalibrationScorer.score(listOf(round(300, 1), round(450, 2), round(600, 0)))
        assertEquals(450, s.wpm)
        assertEquals(Confidence.LOW, s.confidence)
    }

    @Test
    fun partialResults_quitAfterTwoRounds_isHandled() {
        val s = RsvpCalibrationScorer.score(listOf(round(300, 2), round(450, 2)))
        assertEquals(450, s.wpm)
        assertEquals(0.85f, s.punctuationDelayScale, 0.0001f) // 4 of 4
        assertEquals(Confidence.MEDIUM, s.confidence)
    }

    @Test
    fun scaleBoundaries_twoThirdsAndHalf() {
        // 4 of 6 = exactly 2/3 -> 1.0 ; 3 of 6 = exactly 1/2 -> 1.25 ; 2 of 6 -> 1.5
        assertEquals(1.0f, RsvpCalibrationScorer.score(listOf(round(300, 2), round(450, 2), round(600, 0))).punctuationDelayScale, 0.0001f)
        assertEquals(1.25f, RsvpCalibrationScorer.score(listOf(round(300, 2), round(450, 1), round(600, 0))).punctuationDelayScale, 0.0001f)
        assertEquals(1.5f, RsvpCalibrationScorer.score(listOf(round(300, 1), round(450, 1), round(600, 0))).punctuationDelayScale, 0.0001f)
    }

    @Test
    fun wpm_isClampedToTheSupportedRange() {
        assertEquals(RsvpCalibrationPlan.MAX_WPM, RsvpCalibrationScorer.score(listOf(round(1000, 2))).wpm)
        assertEquals(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationScorer.score(listOf(round(50, 2))).wpm)
    }

    @Test
    fun confidence_neverExceedsMedium() {
        val all = Confidence.values().toList()
        assertEquals(listOf(Confidence.LOW, Confidence.MEDIUM), all)
    }
}
