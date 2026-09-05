package com.mckimquyen.reader.ui.page.rsvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _uiState = MutableStateFlow(RsvpUiState())
    val uiState: StateFlow<RsvpUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    fun loadContent(content: String) {
        pause()
        val tokens = RsvpTokenizer.tokenize(content)
        _uiState.update {
            it.copy(
                tokens = tokens,
                currentIndex = 0,
                isPlaying = false,
                isCompleted = false
            )
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
                val baseDelay = (60_000L / _uiState.value.wpm.coerceIn(200, 900))
                val totalDelay = baseDelay + currentToken.extraDelayMs

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
        _uiState.update { it.copy(wpm = wpm.coerceIn(200, 900)) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
