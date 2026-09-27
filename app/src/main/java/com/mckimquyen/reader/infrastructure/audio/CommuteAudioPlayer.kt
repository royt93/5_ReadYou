package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CommutePlayerState(
    val episode: CommuteEpisode? = null,
    val currentDialogueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isCompleted: Boolean = false,
    val isDeepDiveUnlocked: Boolean = false,
    /** True once `TextToSpeech.onInit` has reported success; nothing can be spoken before then. */
    val isTtsReady: Boolean = false,
    /**
     * A play request arrived before the engine was ready and is waiting for it.
     *
     * The UI shows this as "preparing" instead of the silence users used to get, and the request is
     * honoured automatically as soon as initialisation finishes.
     */
    val isAwaitingPlayback: Boolean = false,
    /**
     * Whether the two hosts really get two different voices on this device.
     *
     * Exposed so the UI can say which it is instead of advertising "dual voice" either way.
     */
    val voiceMode: CommuteVoiceMode = CommuteVoiceMode.SIMULATED,
) {
    val currentDialogue: CommuteDialogue?
        get() = episode?.dialogues?.getOrNull(currentDialogueIndex)
}

/**
 * Speaks a two-host radio episode with background lofi audio mixing, ducking, and lock-screen controls.
 *
 * When the device offers at least two usable voices, Alex and Sam get one each. When it does not,
 * playback falls back to shifting pitch and rate on the one available voice, and [CommutePlayerState.voiceMode]
 * says so honestly.
 */
@Singleton
class CommuteAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    val ambientLoop: CommuteAmbientLoop,
) : TextToSpeech.OnInitListener {

    /** Secondary constructor preserving test backward compatibility. */
    constructor(@ApplicationContext context: Context) : this(context, CommuteAmbientLoop(context))

    companion object {
        private const val TAG = "CommuteAudioPlayer"
        private const val UTTERANCE_PREFIX = "COMMUTE_LINE_"

        /** Two real voices already sound different; leave them as the engine recorded them. */
        private const val NEUTRAL_PITCH = 1.0f
        private const val NEUTRAL_RATE = 1.0f

        // Fallback for a device with only one usable voice: shift pitch and rate so the two hosts
        // are at least distinguishable. An imitation, not two voices — the UI says as much.
        private const val SIMULATED_ALEX_PITCH = 0.92f
        private const val SIMULATED_ALEX_RATE = 1.0f
        private const val SIMULATED_SAM_PITCH = 1.28f
        private const val SIMULATED_SAM_RATE = 1.06f
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ambientLoop.setVolume(CommuteAmbientLoop.DUCKED_VOLUME * 0.5f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (_playerState.value.isPlaying) {
                    ambientLoop.setVolume(CommuteAmbientLoop.DUCKED_VOLUME)
                }
            }
        }
    }

    /** Resolved once at init: which voice each host speaks with, or null when there is only one. */
    private var voiceAssignment: CommuteVoiceAssignment? = null

    private val _playerState = MutableStateFlow(CommutePlayerState())
    val playerState: StateFlow<CommutePlayerState> = _playerState.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isInitialized = true
            setupUtteranceListener()

            val assignment = CommuteVoiceSelector.select(
                voices = runCatching { tts?.voices }.getOrNull().orEmpty(),
                targetLocale = Locale.getDefault(),
            )
            voiceAssignment = assignment
            _playerState.update { it.copy(isTtsReady = true, voiceMode = assignment.mode) }
            Log.d(
                TAG,
                "CommuteAudioPlayer TTS ready, voiceMode=${assignment.mode}, " +
                    "alex=${assignment.alex?.name}, sam=${assignment.sam?.name}"
            )

            if (_playerState.value.isAwaitingPlayback) {
                Log.d(TAG, "TTS ready: starting the playback that was waiting for it.")
                speakCurrentDialogue()
            }
        } else {
            Log.e(TAG, "CommuteAudioPlayer TTS init failed with status: $status")
            _playerState.update { it.copy(isTtsReady = false, isAwaitingPlayback = false) }
        }
    }

    /**
     * Waits until the engine is usable, up to [timeoutMs].
     */
    suspend fun awaitReady(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            playerState.map { it.isTtsReady }.first { it }
        } ?: false

    /**
     * Loads [episode] as the current one without speaking a word.
     */
    fun prepareEpisode(episode: CommuteEpisode) {
        _playerState.update {
            it.copy(
                episode = episode,
                currentDialogueIndex = 0,
                isPlaying = false,
                isCompleted = false,
                isAwaitingPlayback = false,
            )
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                handleUtteranceStart(utteranceId)
            }

            override fun onDone(utteranceId: String?) {
                handleUtteranceDone(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleUtteranceError(utteranceId, errorCode = null)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleUtteranceError(utteranceId, errorCode = errorCode)
            }
        })
    }

    @VisibleForTesting
    internal fun handleUtteranceStart(utteranceId: String?) {
        requestAudioFocus()
        ambientLoop.start()
        ambientLoop.setVolume(CommuteAmbientLoop.DUCKED_VOLUME)
        _playerState.update { it.copy(isPlaying = true) }

        val episode = _playerState.value.episode
        val dialogue = _playerState.value.currentDialogue
        if (episode != null && dialogue != null) {
            try {
                CommuteMediaSessionService.start(
                    context = context,
                    title = episode.title,
                    subtitle = "${dialogue.speaker.name}: ${dialogue.text}"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start CommuteMediaSessionService", e)
            }
        }
    }

    @VisibleForTesting
    internal fun handleUtteranceDone(utteranceId: String?) {
        ambientLoop.setVolume(CommuteAmbientLoop.NORMAL_VOLUME)
        scope.launch {
            advanceNextDialogue()
        }
    }

    @VisibleForTesting
    internal fun handleUtteranceError(utteranceId: String?, errorCode: Int?) {
        Log.e(TAG, "Utterance error: $utteranceId, code: $errorCode")
        ambientLoop.pause()
        _playerState.update { it.copy(isPlaying = false) }
        try {
            CommuteMediaSessionService.pause(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not pause CommuteMediaSessionService", e)
        }
    }

    fun playEpisode(episode: CommuteEpisode, startFromIndex: Int = 0) {
        _playerState.update {
            it.copy(
                episode = episode,
                currentDialogueIndex = startFromIndex,
                isCompleted = false
            )
        }
        speakCurrentDialogue()
    }

    fun resume() {
        if (_playerState.value.episode == null) return
        speakCurrentDialogue()
    }

    fun pause() {
        tts?.stop()
        ambientLoop.pause()
        abandonAudioFocus()
        _playerState.update { it.copy(isPlaying = false, isAwaitingPlayback = false) }
        try {
            CommuteMediaSessionService.pause(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not pause CommuteMediaSessionService", e)
        }
    }

    fun skipNext() {
        advanceNextDialogue()
    }

    fun skipPrevious() {
        val currentIndex = _playerState.value.currentDialogueIndex
        if (currentIndex > 0) {
            _playerState.update { it.copy(currentDialogueIndex = currentIndex - 1) }
            speakCurrentDialogue()
        }
    }

    fun seekToDialogue(index: Int) {
        val total = _playerState.value.episode?.dialogues?.size ?: 0
        if (index in 0 until total) {
            _playerState.update { it.copy(currentDialogueIndex = index) }
            speakCurrentDialogue()
        }
    }

    fun unlockDeepDive() {
        _playerState.update { it.copy(isDeepDiveUnlocked = true) }
    }

    private fun advanceNextDialogue() {
        val currentState = _playerState.value
        val episode = currentState.episode ?: return
        val nextIndex = currentState.currentDialogueIndex + 1

        if (nextIndex < episode.dialogues.size) {
            _playerState.update { it.copy(currentDialogueIndex = nextIndex) }
            speakCurrentDialogue()
        } else {
            // Đã hoàn thành toàn bộ tập phát thanh
            tts?.stop()
            ambientLoop.stop()
            abandonAudioFocus()
            _playerState.update { it.copy(isPlaying = false, isCompleted = true) }
            try {
                CommuteMediaSessionService.stop(context)
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop CommuteMediaSessionService", e)
            }
            Log.d(TAG, "CommuteCast Episode completed.")
        }
    }

    private fun speakCurrentDialogue() {
        val currentDialogue = _playerState.value.currentDialogue ?: return

        if (!isInitialized) {
            _playerState.update { it.copy(isAwaitingPlayback = true, isPlaying = false) }
            Log.d(TAG, "TTS not ready yet; playback queued until onInit completes.")
            return
        }

        _playerState.update { it.copy(isAwaitingPlayback = false) }

        applyVoiceFor(currentDialogue.speaker)

        val utteranceId = "$UTTERANCE_PREFIX${_playerState.value.currentDialogueIndex}"
        tts?.speak(currentDialogue.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun applyVoiceFor(speaker: CommuteSpeaker) {
        val assignment = voiceAssignment
        val voice = when (speaker) {
            CommuteSpeaker.ALEX -> assignment?.alex
            CommuteSpeaker.SAM -> assignment?.sam
        }

        if (assignment?.mode == CommuteVoiceMode.REAL_DUAL && voice != null) {
            tts?.voice = voice
            tts?.setPitch(NEUTRAL_PITCH)
            tts?.setSpeechRate(NEUTRAL_RATE)
            return
        }

        when (speaker) {
            CommuteSpeaker.ALEX -> {
                tts?.setPitch(SIMULATED_ALEX_PITCH)
                tts?.setSpeechRate(SIMULATED_ALEX_RATE)
            }
            CommuteSpeaker.SAM -> {
                tts?.setPitch(SIMULATED_SAM_PITCH)
                tts?.setSpeechRate(SIMULATED_SAM_RATE)
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
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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

    fun stopAndReset() {
        tts?.stop()
        ambientLoop.stop()
        abandonAudioFocus()
        try {
            CommuteMediaSessionService.stop(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop CommuteMediaSessionService", e)
        }
        _playerState.update {
            it.copy(
                isPlaying = false,
                currentDialogueIndex = 0,
                isCompleted = false,
                isAwaitingPlayback = false,
            )
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        ambientLoop.stop()
        abandonAudioFocus()
        try {
            CommuteMediaSessionService.stop(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop CommuteMediaSessionService", e)
        }
        isInitialized = false
        voiceAssignment = null
        _playerState.update {
            it.copy(
                isTtsReady = false,
                isAwaitingPlayback = false,
                isPlaying = false,
                voiceMode = CommuteVoiceMode.SIMULATED,
            )
        }
    }
}
