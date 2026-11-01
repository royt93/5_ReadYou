package com.mckimquyen.reader.domain.rsvp

/**
 * Turns calibration round results into a speed suggestion. This is a coarse estimate from very few questions, so the
 * result carries a [Confidence] that is MEDIUM only when the passes form a clean "all slower rounds passed" pattern.
 */
object RsvpCalibrationScorer {
    private const val ALL_CORRECT_SCALE = 0.85f
    private const val GOOD_SCALE = 1.0f
    private const val FAIR_SCALE = 1.25f
    private const val POOR_SCALE = 1.5f
    private const val GOOD_ACCURACY = 2f / 3f
    private const val FAIR_ACCURACY = 0.5f

    fun score(results: List<RoundResult>): RsvpSuggestion {
        if (results.isEmpty()) {
            return RsvpSuggestion(
                wpm = RsvpCalibrationPlan.FALLBACK_WPM,
                punctuationDelayScale = RsvpCalibrationPlan.DEFAULT_DELAY_SCALE,
                confidence = Confidence.LOW,
                passedRounds = emptyList(),
            )
        }

        val passed = results.filter { it.correct >= RsvpCalibrationPlan.PASS_CORRECT }.map { it.wpm }.sorted()
        val highestPassed = passed.lastOrNull()
        val wpm = (highestPassed ?: RsvpCalibrationPlan.FALLBACK_WPM)
            .coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM)

        val totalCorrect = results.sumOf { it.correct }
        val totalAsked = results.sumOf { it.total }.coerceAtLeast(1)
        val accuracy = totalCorrect.toFloat() / totalAsked
        val scale = when {
            totalCorrect == totalAsked -> ALL_CORRECT_SCALE
            accuracy >= GOOD_ACCURACY -> GOOD_SCALE
            accuracy >= FAIR_ACCURACY -> FAIR_SCALE
            else -> POOR_SCALE
        }.coerceIn(RsvpCalibrationPlan.MIN_DELAY_SCALE, RsvpCalibrationPlan.MAX_DELAY_SCALE)

        val monotonic = highestPassed != null &&
            results.filter { it.wpm < highestPassed }.all { it.correct >= RsvpCalibrationPlan.PASS_CORRECT }

        return RsvpSuggestion(
            wpm = wpm,
            punctuationDelayScale = scale,
            confidence = if (monotonic) Confidence.MEDIUM else Confidence.LOW,
            passedRounds = passed,
        )
    }
}
