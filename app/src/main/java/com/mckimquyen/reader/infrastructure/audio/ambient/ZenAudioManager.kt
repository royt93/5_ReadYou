package com.mckimquyen.reader.infrastructure.audio.ambient

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZenAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val synthesizer = ZenSoundSynthesizer()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentType = MutableStateFlow(ZenSoundType.GENTLE_RAIN)
    val currentType: StateFlow<ZenSoundType> = _currentType.asStateFlow()

    private val _volume = MutableStateFlow(0.6f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0) // 0 = continuous
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private var sleepJob: Job? = null

    fun play(type: ZenSoundType? = null) {
        val targetType = type ?: _currentType.value
        _currentType.value = targetType
        _isPlaying.value = true
        synthesizer.start(targetType, _volume.value)
        scheduleSleepTimer(_sleepTimerMinutes.value)
    }

    fun stop() {
        _isPlaying.value = false
        sleepJob?.cancel()
        sleepJob = null
        synthesizer.stop()
    }

    fun toggle(type: ZenSoundType? = null) {
        if (_isPlaying.value) {
            if (type != null && type != _currentType.value) {
                // Switch sound type
                play(type)
            } else {
                stop()
            }
        } else {
            play(type)
        }
    }

    fun setSoundType(type: ZenSoundType) {
        _currentType.value = type
        if (_isPlaying.value) {
            synthesizer.setSoundType(type)
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        synthesizer.setVolume(clamped)
    }

    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        if (_isPlaying.value) {
            scheduleSleepTimer(minutes)
        }
    }

    private fun scheduleSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) return

        sleepJob = scope.launch {
            delay(minutes * 60 * 1000L)
            stop()
        }
    }
}
