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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CommutePlayerState(
    val episode: CommuteEpisode? = null,
    val currentDialogueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isCompleted: Boolean = false,
    val isDeepDiveUnlocked: Boolean = false,
) {
    val currentDialogue: CommuteDialogue?
        get() = episode?.dialogues?.getOrNull(currentDialogueIndex)
}

/**
 * Trình phát âm thanh radio song thoại 2 MC (Dual-Voice TTS) với chuyển đổi cao độ và nhịp độ tự động.
 */
@Singleton
class CommuteAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CommuteAudioPlayer"
        private const val UTTERANCE_PREFIX = "COMMUTE_LINE_"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
            Log.d(TAG, "CommuteAudioPlayer TTS initialized successfully.")
        } else {
            Log.e(TAG, "CommuteAudioPlayer TTS init failed with status: $status")
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
        _playerState.update {
            it.copy(
                episode = episode,
                currentDialogueIndex = startFromIndex,
                isPlaying = true,
                isCompleted = false
            )
        }
        speakCurrentDialogue()
    }

    fun resume() {
        if (_playerState.value.episode == null) return
        _playerState.update { it.copy(isPlaying = true) }
        speakCurrentDialogue()
    }

    fun pause() {
        tts?.stop()
        _playerState.update { it.copy(isPlaying = false) }
    }

    fun skipNext() {
        advanceNextDialogue()
    }

    fun skipPrevious() {
        val currentIndex = _playerState.value.currentDialogueIndex
        if (currentIndex > 0) {
            _playerState.update { it.copy(currentDialogueIndex = currentIndex - 1, isPlaying = true) }
            speakCurrentDialogue()
        }
    }

    fun seekToDialogue(index: Int) {
        val total = _playerState.value.episode?.dialogues?.size ?: 0
        if (index in 0 until total) {
            _playerState.update { it.copy(currentDialogueIndex = index, isPlaying = true) }
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
            _playerState.update { it.copy(currentDialogueIndex = nextIndex, isPlaying = true) }
            speakCurrentDialogue()
        } else {
            // Đã hoàn thành toàn bộ tập phát thanh
            _playerState.update { it.copy(isPlaying = false, isCompleted = true) }
            Log.d(TAG, "CommuteCast Episode completed.")
        }
    }

    private fun speakCurrentDialogue() {
        if (!isInitialized) return
        val currentDialogue = _playerState.value.currentDialogue ?: return

        // Điều chỉnh cao độ và tốc độ nói theo từng nhân vật
        when (currentDialogue.speaker) {
            CommuteSpeaker.ALEX -> {
                tts?.setPitch(0.92f)        // Giọng nam trầm ấm, điềm tĩnh
                tts?.setSpeechRate(1.0f)     // Tốc độ bình thường
            }
            CommuteSpeaker.SAM -> {
                tts?.setPitch(1.28f)        // Giọng nữ năng động, tươi sáng
                tts?.setSpeechRate(1.06f)    // Nhịp độ nhanh hơn đôi chút
            }
        }

        val utteranceId = "$UTTERANCE_PREFIX${_playerState.value.currentDialogueIndex}"
        tts?.speak(currentDialogue.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopAndReset() {
        tts?.stop()
        _playerState.update {
            it.copy(
                isPlaying = false,
                currentDialogueIndex = 0,
                isCompleted = false
            )
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
    }
}
