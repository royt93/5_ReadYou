package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPacerTest {

    @Test
    fun theFirstTwoActionsNeverShowAnAd_theThirdDoes() {
        val p = InterstitialPacer(everyNthAction = 3)
        assertEquals(listOf(false, false, true), List(3) { p.onUserAction() })
    }

    @Test
    fun afterAnAdTheCountStartsAgain() {
        val p = InterstitialPacer(everyNthAction = 3)
        val shown = List(9) { p.onUserAction() }
        assertEquals(listOf(false, false, true, false, false, true, false, false, true), shown)
    }

    @Test
    fun neverMoreThanOneAdPerTwoActions_theAdMobRule() {
        val p = InterstitialPacer()
        val shown = List(100) { p.onUserAction() }
        assertFalse("two ads in a row", shown.zipWithNext().any { (a, b) -> a && b })
        assertTrue("an ad is shown sometimes", shown.any { it })
        assertTrue("the default gap is at least 3 actions", InterstitialPacer.DEFAULT_EVERY_NTH_ACTION >= 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aGapBelowThreeIsRefused_itWouldBreakTheAdMobRule() {
        InterstitialPacer(everyNthAction = 2)
    }
}
