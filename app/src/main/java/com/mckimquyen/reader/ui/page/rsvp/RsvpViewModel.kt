package com.mckimquyen.reader.ui.page.rsvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan
import com.mckimquyen.reader.domain.rsvp.RsvpTiming
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RsvpUiState(
    val tokens: List<RsvpToken> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val wpm: Int = 350,
    val isCompleted: Boolean = false
) {
    val currentToken: RsvpToken?
        get() = tokens.getOrNull(currentIndex)

    val progress: Float
        get() = if (tokens.isNotEmpty()) (currentIndex.toFloat() / (tokens.size - 1).coerceAtLeast(1)) else 0f

    val remainingWords: Int
        get() = (tokens.size - currentIndex).coerceAtLeast(0)

    val timeRemainingSeconds: Int
        get() = if (wpm > 0) (remainingWords * 60) / wpm else 0

    fun formattedTimeRemaining(): String {
        val totalSecs = timeRemainingSeconds
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
}

@HiltViewModel
class RsvpViewModel @Inject constructor() : ViewModel() {

    /** Hilt field injection preserves the public no-argument constructor used by existing tests and previews. */
    @Inject lateinit var injectedCalibrationStore: RsvpCalibrationStore

    /** Test-only override; ordinary `RsvpViewModel()` remains source-compatible. */
    private var calibrationStoreOverride: RsvpCalibrationStore? = null

    internal constructor(store: RsvpCalibrationStore?) : this() {
        calibrationStoreOverride = store
    }

    private val calibrationStore: RsvpCalibrationStore?
        get() = calibrationStoreOverride ?: if (::injectedCalibrationStore.isInitialized) injectedCalibrationStore else null

    private var punctuationDelayScale = RsvpCalibrationPlan.DEFAULT_DELAY_SCALE
    private var wpmChosenByUser = false

    private val _uiState = MutableStateFlow(RsvpUiState())
    val uiState: StateFlow<RsvpUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    fun loadContent(content: String) {
        pause()
        val tokens = RsvpTokenizer.tokenize(content, punctuationDelayScale)
        _uiState.update {
            it.copy(
                tokens = tokens,
                currentIndex = 0,
                isPlaying = false,
                isCompleted = false
            )
        }
        applySavedSettings(content)
    }

    /**
     * Applies the calibrated speed and punctuation scale once per load. A speed the user already picked this session
     * wins over the saved one, and a failed read leaves the defaults untouched.
     */
    private fun applySavedSettings(content: String) {
        val store = calibrationStore ?: return
        viewModelScope.launch {
            val saved = try {
                store.read()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@launch
            punctuationDelayScale = saved.punctuationDelayScale
            _uiState.update { state ->
                state.copy(
                    tokens = RsvpTokenizer.tokenize(content, punctuationDelayScale),
                    wpm = if (wpmChosenByUser) state.wpm else saved.wpm.coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM),
                )
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val state = _uiState.value
        if (state.tokens.isEmpty()) return

        var startIndex = state.currentIndex
        if (startIndex >= state.tokens.size - 1 || state.isCompleted) {
            startIndex = 0
            _uiState.update { it.copy(currentIndex = 0, isCompleted = false) }
        }

        _uiState.update { it.copy(isPlaying = true, isCompleted = false) }

        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_uiState.value.isPlaying && _uiState.value.currentIndex < _uiState.value.tokens.size) {
                val currentToken = _uiState.value.tokens[_uiState.value.currentIndex]
                val totalDelay = RsvpTiming.tokenDelayMs(_uiState.value.wpm, currentToken.extraDelayMs)

                delay(totalDelay)

                if (!_uiState.value.isPlaying) break

                val nextIndex = _uiState.value.currentIndex + 1
                if (nextIndex < _uiState.value.tokens.size) {
                    _uiState.update { it.copy(currentIndex = nextIndex) }
                } else {
                    _uiState.update { it.copy(isPlaying = false, isCompleted = true) }
                    break
                }
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(index: Int) {
        val maxIndex = (_uiState.value.tokens.size - 1).coerceAtLeast(0)
        val target = index.coerceIn(0, maxIndex)
        _uiState.update { it.copy(currentIndex = target, isCompleted = target >= maxIndex) }
    }

    fun step(offset: Int) {
        val target = _uiState.value.currentIndex + offset
        seekTo(target)
    }

    fun setWpm(wpm: Int) {
        wpmChosenByUser = true
        _uiState.update { it.copy(wpm = wpm.coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM)) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
