package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        tts = TextToSpeech(context, this)
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

    fun play(text: String) {
        Log.d("roy93~", "TtsManager play text length: ${text.length}")
        if (!isInitialized) {
            Log.d("roy93~", "TtsManager play aborted: not initialized")
            return
        }
        
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        if (text.length > maxLength) {
            Log.d("roy93~", "TtsManager play: text length > maxLength ($maxLength), chunking...")
            val chunks = text.chunked(maxLength - 100)
            chunks.forEachIndexed { index, chunk ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(chunk, queueMode, null, "TTS_ID_$index")
            }
        } else {
            Log.d("roy93~", "TtsManager play: text length OK")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
        }
        _ttsState.value = TtsState.PLAYING
    }

    fun stop() {
        Log.d("roy93~", "TtsManager stop()")
        if (!isInitialized) return
        tts?.stop()
        _ttsState.value = TtsState.IDLE
    }

    fun shutdown() {
        Log.d("roy93~", "TtsManager shutdown()")
        if (!isInitialized) return
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        _ttsState.value = TtsState.IDLE
    }
}
