package com.mckimquyen.reader.ui.page.rsvp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RsvpViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RsvpViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadContent_initializesTokensAndState() {
        viewModel.loadContent("One two three four five")
        val state = viewModel.uiState.value

        assertEquals(5, state.tokens.size)
        assertEquals(0, state.currentIndex)
        assertFalse(state.isPlaying)
        assertFalse(state.isCompleted)
    }

    @Test
    fun loadContent_startsAtRequestedFraction() {
        viewModel.loadContent((1..9).joinToString(" ") { "word$it" }, startFraction = 0.5f)
        assertEquals(4, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun loadContent_clampsStartFraction_andDefaultsToStart() {
        viewModel.loadContent("One Two Three", startFraction = 3f)
        assertEquals(2, viewModel.uiState.value.currentIndex)

        viewModel.loadContent("One Two Three")
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun seekTo_clampsWithinBounds() {
        viewModel.loadContent("Alpha Beta Gamma")

        viewModel.seekTo(1)
        assertEquals(1, viewModel.uiState.value.currentIndex)

        viewModel.seekTo(999)
        assertEquals(2, viewModel.uiState.value.currentIndex)

        viewModel.seekTo(-5)
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun step_advancesAndRewinds() {
        val words = (1..30).joinToString(" ") { "word$it" }
        viewModel.loadContent(words)

        viewModel.seekTo(15)
        viewModel.step(10)
        assertEquals(25, viewModel.uiState.value.currentIndex)

        viewModel.step(-10)
        assertEquals(15, viewModel.uiState.value.currentIndex)

        viewModel.step(-50)
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun setWpm_clampsToMinMax() {
        viewModel.setWpm(500)
        assertEquals(500, viewModel.uiState.value.wpm)

        viewModel.setWpm(50) // below 200
        assertEquals(200, viewModel.uiState.value.wpm)

        viewModel.setWpm(1500) // above 900
        assertEquals(900, viewModel.uiState.value.wpm)
    }

    @Test
    fun timeRemaining_formatsCorrectly() {
        // 600 words at 300 WPM = 2 minutes remaining = 120 seconds
        val words = (1..600).joinToString(" ") { "w$it" }
        viewModel.loadContent(words)
        viewModel.setWpm(300)

        val state = viewModel.uiState.value
        assertEquals(120, state.timeRemainingSeconds)
        assertEquals("2m 0s", state.formattedTimeRemaining())
    }

    @Test
    fun togglePlayPause_updatesState() {
        viewModel.loadContent("Testing play pause functionality")
        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayPause()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayPause()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    private class FakeStore(private val saved: SavedRsvpSettings?) : RsvpCalibrationStore {
        override suspend fun read(): SavedRsvpSettings? = saved
        override suspend fun write(wpm: Int, punctuationDelayScale: Float) = Unit
    }

    private fun vmWith(saved: SavedRsvpSettings?) = RsvpViewModel(FakeStore(saved) as RsvpCalibrationStore?)

    @Test
    fun withoutStore_keepsTheDefaultSpeed() {
        val vm = RsvpViewModel()
        vm.loadContent("One two three")
        assertEquals(350, vm.uiState.value.wpm)
    }

    @Test
    fun uncalibratedStore_keepsTheDefaultSpeed() = runTest(testDispatcher) {
        val vm = vmWith(null)
        vm.loadContent("One two three")
        advanceUntilIdle()
        assertEquals(350, vm.uiState.value.wpm)
    }

    @Test
    fun savedSpeed_isAppliedOnLoad() = runTest(testDispatcher) {
        val vm = vmWith(SavedRsvpSettings(wpm = 500, punctuationDelayScale = 1.0f))
        vm.loadContent("One two three")
        advanceUntilIdle()
        assertEquals(500, vm.uiState.value.wpm)
    }

    @Test
    fun savedScale_changesPunctuationDelaysOfTheLoadedTokens() = runTest(testDispatcher) {
        val vm = vmWith(SavedRsvpSettings(wpm = 400, punctuationDelayScale = 2.0f))
        vm.loadContent("Stop. Go")
        advanceUntilIdle()
        assertEquals(400L, vm.uiState.value.tokens.first().extraDelayMs) // sentence end 200 * 2
    }

    @Test
    fun manualSpeedChosenBeforeTheStoreAnswers_wins() = runTest(testDispatcher) {
        val vm = vmWith(SavedRsvpSettings(wpm = 500, punctuationDelayScale = 1.0f))
        vm.loadContent("One two three")
        vm.setWpm(420) // user moves the slider before the saved value arrives
        advanceUntilIdle()
        assertEquals(420, vm.uiState.value.wpm)
    }

    @Test
    fun savedSpeed_isClampedToTheSupportedRange() = runTest(testDispatcher) {
        val vm = vmWith(SavedRsvpSettings(wpm = 5000, punctuationDelayScale = 1.0f))
        vm.loadContent("One two three")
        advanceUntilIdle()
        assertEquals(900, vm.uiState.value.wpm)
    }
}
