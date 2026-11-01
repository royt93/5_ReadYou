package com.mckimquyen.reader.domain.rsvp

private const val MILLIS_PER_MINUTE = 60_000L

/** The per-token delay formula shared by the reader and the calibration, so both play at the same real speed. */
object RsvpTiming {
    fun baseDelayMs(wpm: Int): Long =
        MILLIS_PER_MINUTE / wpm.coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM)

    fun tokenDelayMs(wpm: Int, extraDelayMs: Long): Long = baseDelayMs(wpm) + extraDelayMs
}
