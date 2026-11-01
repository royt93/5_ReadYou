package com.mckimquyen.reader.domain.rsvp

import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpTimingTest {

    @Test
    fun baseDelay_isSixtyThousandOverWpm() {
        assertEquals(200L, RsvpTiming.baseDelayMs(300))
        assertEquals(150L, RsvpTiming.baseDelayMs(400))
        assertEquals(100L, RsvpTiming.baseDelayMs(600))
    }

    @Test
    fun baseDelay_clampsWpmToTheSupportedRange() {
        assertEquals(300L, RsvpTiming.baseDelayMs(100)) // clamped up to 200 WPM
        assertEquals(66L, RsvpTiming.baseDelayMs(1500)) // clamped down to 900 WPM
    }

    @Test
    fun tokenDelay_addsTheTokenExtraDelay() {
        assertEquals(450L, RsvpTiming.tokenDelayMs(wpm = 300, extraDelayMs = 250L))
        assertEquals(100L, RsvpTiming.tokenDelayMs(wpm = 600, extraDelayMs = 0L))
    }
}
