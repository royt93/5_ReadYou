package com.mckimquyen.reader.infrastructure.audio.ambient

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var preDuckVolume: Float? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentType = MutableStateFlow(ZenSoundType.GENTLE_RAIN)
    val currentType: StateFlow<ZenSoundType> = _currentType.asStateFlow()

    private val _volume = MutableStateFlow(0.6f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0) // 0 = continuous
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private var sleepJob: Job? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (preDuckVolume == null) preDuckVolume = _volume.value
                synthesizer.setVolume((_volume.value * 0.2f).coerceAtLeast(0.05f))
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                preDuckVolume?.let {
                    synthesizer.setVolume(it)
                    preDuckVolume = null
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun play(type: ZenSoundType? = null) {
        requestAudioFocus()
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
        abandonAudioFocus()
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
