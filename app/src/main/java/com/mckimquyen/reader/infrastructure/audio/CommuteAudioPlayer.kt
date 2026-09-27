package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
 * Speaks a two-host radio episode.
 *
 * When the device offers at least two usable voices, Alex and Sam get one each, which is a real
 * difference in voice. When it does not, playback falls back to shifting pitch and rate on the one
 * available voice — a rough imitation, and [CommutePlayerState.voiceMode] says so, so the UI can
 * tell the user rather than claiming two voices it does not have.
 */
@Singleton
class CommuteAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextToSpeech.OnInitListener {

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

            // Resolve the voices once here rather than per line: getVoices() queries the engine and
            // the answer cannot change while it is running.
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

            // Honour a play request that arrived while the engine was still starting, instead of
            // leaving the user with a notification they tapped and nothing to hear.
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
     *
     * The morning worker calls this before notifying, so a notification is never sent promising
     * audio the device cannot actually produce.
     */
    suspend fun awaitReady(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            playerState.map { it.isTtsReady }.first { it }
        } ?: false

    /**
     * Loads [episode] as the current one without speaking a word.
     *
     * Replaces the old trick of calling [playEpisode] and then [pause]: that briefly queued a real
     * utterance, so a background job could emit a burst of speech before stopping it.
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
                _playerState.update { it.copy(isPlaying = true) }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    advanceNextDialogue()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Utterance error: $utteranceId")
                _playerState.update { it.copy(isPlaying = false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "Utterance error: $utteranceId, code: $errorCode")
                _playerState.update { it.copy(isPlaying = false) }
            }
        })
    }

    fun playEpisode(episode: CommuteEpisode, startFromIndex: Int = 0) {
        // isPlaying is not set here: only the engine's own onStart callback knows playback really
        // began. Claiming it up front left the UI stuck showing "playing" over silence whenever the
        // engine was not ready yet.
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
        // Clearing the pending request matters: without it, a user who pauses while the engine is
        // still starting would be surprised by playback beginning on its own once it is ready.
        _playerState.update { it.copy(isPlaying = false, isAwaitingPlayback = false) }
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
            _playerState.update { it.copy(isPlaying = false, isCompleted = true) }
            Log.d(TAG, "CommuteCast Episode completed.")
        }
    }

    private fun speakCurrentDialogue() {
        val currentDialogue = _playerState.value.currentDialogue ?: return

        if (!isInitialized) {
            // Remember the request rather than dropping it silently, and tell the UI we are waiting
            // so the user sees "preparing" instead of a dead button.
            _playerState.update { it.copy(isAwaitingPlayback = true, isPlaying = false) }
            Log.d(TAG, "TTS not ready yet; playback queued until onInit completes.")
            return
        }

        _playerState.update { it.copy(isAwaitingPlayback = false) }

        applyVoiceFor(currentDialogue.speaker)

        val utteranceId = "$UTTERANCE_PREFIX${_playerState.value.currentDialogueIndex}"
        tts?.speak(currentDialogue.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Makes [speaker] sound like themselves.
     *
     * With two real voices available, each host simply gets their own and pitch stays neutral —
     * bending a distinct voice on top of that only makes it sound synthetic. With one voice, pitch
     * and rate are all there is to work with, which is an imitation, not two voices.
     */
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

    fun stopAndReset() {
        tts?.stop()
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
        isInitialized = false
        // The voices belonged to the engine that just went away; a re-init has to resolve its own.
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
