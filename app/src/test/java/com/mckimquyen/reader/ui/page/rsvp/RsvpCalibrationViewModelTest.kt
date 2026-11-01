package com.mckimquyen.reader.ui.page.rsvp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpCalibrationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val savedWrites = mutableListOf<SavedRsvpSettings>()
    private val fakeStore = object : RsvpCalibrationStore {
        override suspend fun read(): SavedRsvpSettings? = savedWrites.lastOrNull()
        override suspend fun write(wpm: Int, punctuationDelayScale: Float) {
            savedWrites.add(SavedRsvpSettings(wpm, punctuationDelayScale))
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        savedWrites.clear()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(lang: String = "en") = RsvpCalibrationViewModel(
        calibrationStore = fakeStore,
        defaultDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        languageTag = { lang },
    )

    @Test fun startsAtIntro() {
        val vm = createViewModel()
        assertEquals(CalibrationStep.Intro, vm.step.value)
    }

    @Test fun startCalibration_movesToReadingRoundZeroAt300Wpm() {
        val vm = createViewModel()
        vm.startCalibration()
        val step = vm.step.value as CalibrationStep.Reading
        assertEquals(0, step.roundIndex)
        assertEquals(300, step.wpm)
    }

    @Test fun passageFinished_movesToQuestionsForThatRound() {
        val vm = createViewModel()
        vm.startCalibration()
        vm.onPassageFinished()
        val step = vm.step.value as CalibrationStep.Questions
        assertEquals(0, step.roundIndex)
        assertEquals(2, step.questions.size)
    }

    @Test fun submittingRoundAnswers_movesToNextReadingRound() {
        val vm = createViewModel()
        vm.startCalibration()
        vm.onPassageFinished()
        vm.submitAnswers(listOf(1, 2)) // answer round 0
        val step = vm.step.value as CalibrationStep.Reading
        assertEquals(1, step.roundIndex)
        assertEquals(450, step.wpm)
    }

    @Test fun fullThreeRounds_movesToResult_andApplySavesToStore() = runTest(testDispatcher) {
        val vm = createViewModel("en")
        vm.startCalibration()

        // Round 0 (300 WPM): answers (1, 2) are correct in EN
        vm.onPassageFinished()
        vm.submitAnswers(listOf(1, 2))

        // Round 1 (450 WPM): answers (1, 0) are correct in EN
        vm.onPassageFinished()
        vm.submitAnswers(listOf(1, 0))

        // Round 2 (600 WPM): answers (2, 0) are correct in EN
        vm.onPassageFinished()
        vm.submitAnswers(listOf(2, 0))

        val step = vm.step.value as CalibrationStep.Result
        assertEquals(600, step.suggestion.wpm)

        var reportedWpm = 0
        vm.applySuggestion { reportedWpm = it.wpm }
        advanceUntilIdle()

        assertEquals(1, savedWrites.size)
        assertEquals(600, savedWrites.first().wpm)
        assertEquals(600, reportedWpm)
        assertEquals(CalibrationStep.Finished, vm.step.value)
    }

    @Test fun cancel_savesNothingToStore_andReturnsToIntro() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.startCalibration()
        vm.cancel()
        advanceUntilIdle()

        assertTrue(savedWrites.isEmpty())
        assertEquals(CalibrationStep.Intro, vm.step.value)
    }
}
