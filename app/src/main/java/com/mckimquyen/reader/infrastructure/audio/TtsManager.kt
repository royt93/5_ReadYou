package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class TtsState {
    IDLE, PLAYING, PLAY_ERROR
}

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _ttsState = MutableStateFlow(TtsState.IDLE)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val _completions = MutableSharedFlow<Unit>(extraBufferCapacity = COMPLETION_BUFFER)

    /** Phát đúng một lần khi đoạn đọc CUỐI của một lần [play] kết thúc; không phát sau pause/stop/shutdown. */
    val completions: SharedFlow<Unit> = _completions.asSharedFlow()

    /** Id utterance cuối của lần [play] hiện tại; null khi không còn gì đang chờ hoàn thành. */
    private var lastUtteranceId: String? = null

    private val _speechRate = MutableStateFlow(DEFAULT_SPEECH_RATE)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    var currentTitle: String = ""
        private set
    var currentSubtitle: String = ""
        private set
    var lastPlayedText: String = ""
        private set

    init {
        tts = TextToSpeech(context, this)
    }

    fun setSpeechRate(rate: Float): Boolean {
        val clamped = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        val success = tts?.setSpeechRate(clamped) == TextToSpeech.SUCCESS
        if (success) {
            _speechRate.value = clamped
        }
        return success
    }

    override fun onInit(status: Int) {
        Log.d("roy93~", "TtsManager onInit status: $status")
        if (status == TextToSpeech.SUCCESS) {
            // Use system locale instead of hardcoded vi_VN:
            // this app serves 6 languages (EN/VI/ZH/JA/FR/DE) and articles
            // may be in any language — forcing Vietnamese for all content is wrong.
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("roy93~", "TtsManager Language not supported, trying en_US fallback")
                tts?.setLanguage(Locale.US)
            }
            isInitialized = true
            
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("roy93~", "TtsManager onStart: $utteranceId")
                    _ttsState.value = TtsState.PLAYING
                }
                override fun onDone(utteranceId: String?) {
                    Log.d("roy93~", "TtsManager onDone: $utteranceId")
                    _ttsState.value = TtsState.IDLE
                    if (utteranceId != null && utteranceId == lastUtteranceId) {
                        lastUtteranceId = null
                        _completions.tryEmit(Unit)
                    }
                }
                @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId: String?, errorCode: Int)"))
                override fun onError(utteranceId: String?) {
                    Log.e("roy93~", "TtsManager onError(String): $utteranceId")
                    _ttsState.value = TtsState.PLAY_ERROR
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e("roy93~", "TtsManager onError(String, Int): $utteranceId, code: $errorCode")
                    _ttsState.value = TtsState.PLAY_ERROR
                }
            })
        } else {
            Log.e("roy93~", "TtsManager Initialization Failed!")
            _ttsState.value = TtsState.PLAY_ERROR
        }
    }

    fun play(text: String, title: String = "", subtitle: String = "") {
        Log.d("roy93~", "TtsManager play text length: ${text.length}")
        lastPlayedText = text
        if (title.isNotBlank()) currentTitle = title
        if (subtitle.isNotBlank()) currentSubtitle = subtitle

        if (!isInitialized) {
            Log.d("roy93~", "TtsManager play aborted: not initialized")
            return
        }

        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        if (text.length > maxLength) {
            Log.d("roy93~", "TtsManager play: text length > maxLength ($maxLength), chunking...")
            val chunks = text.chunked(maxLength - 100)
            lastUtteranceId = "$UTTERANCE_ID_PREFIX${chunks.lastIndex}"
            chunks.forEachIndexed { index, chunk ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(chunk, queueMode, null, "$UTTERANCE_ID_PREFIX$index")
            }
        } else {
            Log.d("roy93~", "TtsManager play: text length OK")
            lastUtteranceId = UTTERANCE_ID
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
        _ttsState.value = TtsState.PLAYING
        TtsForegroundService.start(context, currentTitle, currentSubtitle)
    }

    /**
     * Đọc một câu thông báo ngắn (không có tin, hết tin) mà KHÔNG đụng tới trạng thái phát lại: không ghi đè
     * [lastPlayedText]/tiêu đề và không phát [completions], để nút Play sau đó vẫn đọc lại đúng bài.
     */
    fun speakNotice(text: String) {
        if (!isInitialized || text.isBlank()) return
        lastUtteranceId = null
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, NOTICE_UTTERANCE_ID)
    }

    fun pause() {
        Log.d("roy93~", "TtsManager pause()")
        lastUtteranceId = null
        if (!isInitialized) return
        tts?.stop()
        _ttsState.value = TtsState.IDLE
        TtsForegroundService.pause(context)
    }

    fun resume() {
        Log.d("roy93~", "TtsManager resume()")
        if (lastPlayedText.isNotBlank()) {
            play(lastPlayedText, currentTitle, currentSubtitle)
        }
    }

    fun stop() {
        Log.d("roy93~", "TtsManager stop()")
        lastUtteranceId = null
        if (!isInitialized) return
        tts?.stop()
        _ttsState.value = TtsState.IDLE
        TtsForegroundService.stop(context)
    }

    fun shutdown() {
        Log.d("roy93~", "TtsManager shutdown()")
        lastUtteranceId = null
        if (!isInitialized) return
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        _ttsState.value = TtsState.IDLE
        TtsForegroundService.stop(context)
    }

    companion object {
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val COMPLETION_BUFFER = 1
        private const val UTTERANCE_ID = "TTS_ID"
        private const val NOTICE_UTTERANCE_ID = "TTS_NOTICE"
        private const val UTTERANCE_ID_PREFIX = "TTS_ID_"
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.5f

        val SUPPORTED_SPEECH_RATES = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }
}
