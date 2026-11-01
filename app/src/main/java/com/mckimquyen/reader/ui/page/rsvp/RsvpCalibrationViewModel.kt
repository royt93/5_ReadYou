package com.mckimquyen.reader.ui.page.rsvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.rsvp.CalibrationQuestion
import com.mckimquyen.reader.domain.rsvp.RoundResult
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationContent
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationScorer
import com.mckimquyen.reader.domain.rsvp.RsvpSuggestion
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

sealed interface CalibrationStep {
    object Intro : CalibrationStep
    data class Reading(val roundIndex: Int, val wpm: Int, val text: String) : CalibrationStep
    data class Questions(val roundIndex: Int, val wpm: Int, val questions: List<CalibrationQuestion>) : CalibrationStep
    data class Result(val suggestion: RsvpSuggestion) : CalibrationStep
    object Finished : CalibrationStep
}

/**
 * Three-round speed calibration. Leaving at any point saves nothing; only [applySuggestion] writes to the store.
 */
@HiltViewModel
class RsvpCalibrationViewModel @Inject constructor(
    private val calibrationStore: RsvpCalibrationStore,
    @DefaultDispatcher @Suppress("unused") private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Replaceable in tests; production reads the device language. */
    internal var languageTag: () -> String = { Locale.getDefault().language }

    /** Test constructor: the single `@Inject` constructor above is the only one Hilt sees. */
    internal constructor(
        calibrationStore: RsvpCalibrationStore,
        defaultDispatcher: CoroutineDispatcher,
        ioDispatcher: CoroutineDispatcher,
        languageTag: () -> String,
    ) : this(calibrationStore, defaultDispatcher, ioDispatcher) {
        this.languageTag = languageTag
    }

    private val _step = MutableStateFlow<CalibrationStep>(CalibrationStep.Intro)
    val step: StateFlow<CalibrationStep> = _step.asStateFlow()

    private val results = mutableListOf<RoundResult>()

    private val passages get() = RsvpCalibrationContent.forLanguage(languageTag())

    fun startCalibration() {
        results.clear()
        showReading(0)
    }

    fun onPassageFinished() {
        val reading = _step.value as? CalibrationStep.Reading ?: return
        _step.value = CalibrationStep.Questions(reading.roundIndex, reading.wpm, passages[reading.roundIndex].questions)
    }

    /** [selectedIndices] holds one chosen option index per question of the current round, in order. */
    fun submitAnswers(selectedIndices: List<Int>) {
        val current = _step.value as? CalibrationStep.Questions ?: return
        val correct = current.questions.indices.count { i -> selectedIndices.getOrNull(i) == current.questions[i].correctIndex }
        results += RoundResult(wpm = current.wpm, correct = correct, total = current.questions.size)
        val next = current.roundIndex + 1
        if (next < RsvpCalibrationPlan.ROUND_WPMS.size) {
            showReading(next)
        } else {
            _step.value = CalibrationStep.Result(RsvpCalibrationScorer.score(results.toList()))
        }
    }

    /** Saves the suggestion, then reports it so the caller can set the reader's speed. No-op outside the Result step. */
    fun applySuggestion(onApplied: (RsvpSuggestion) -> Unit = {}) {
        val suggestion = (_step.value as? CalibrationStep.Result)?.suggestion ?: return
        viewModelScope.launch {
            withContext(ioDispatcher) { calibrationStore.write(suggestion.wpm, suggestion.punctuationDelayScale) }
            _step.value = CalibrationStep.Finished
            onApplied(suggestion)
        }
    }

    /** Leaves without saving anything. */
    fun cancel() {
        results.clear()
        _step.value = CalibrationStep.Intro
    }

    private fun showReading(roundIndex: Int) {
        val wpm = RsvpCalibrationPlan.ROUND_WPMS[roundIndex]
        _step.value = CalibrationStep.Reading(roundIndex, wpm, passages[roundIndex].text)
    }
}
