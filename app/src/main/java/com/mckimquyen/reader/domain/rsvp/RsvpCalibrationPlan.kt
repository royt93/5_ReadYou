package com.mckimquyen.reader.domain.rsvp

/** Never higher than MEDIUM: six multiple-choice questions cannot justify more. */
enum class Confidence { LOW, MEDIUM }

data class RoundResult(val wpm: Int, val correct: Int, val total: Int = RsvpCalibrationPlan.QUESTIONS_PER_ROUND)

data class RsvpSuggestion(
    val wpm: Int,
    val punctuationDelayScale: Float,
    val confidence: Confidence,
    val passedRounds: List<Int>,
)

object RsvpCalibrationPlan {
    val ROUND_WPMS: List<Int> = listOf(300, 450, 600)
    const val QUESTIONS_PER_ROUND = 2
    const val PASS_CORRECT = 2
    const val FALLBACK_WPM = 250
    const val DEFAULT_WPM = 350
    const val MIN_WPM = 200
    const val MAX_WPM = 900
    const val MIN_DELAY_SCALE = 0.5f
    const val MAX_DELAY_SCALE = 2.0f
    const val DEFAULT_DELAY_SCALE = 1.0f
}
