package com.mckimquyen.reader.infrastructure.audio

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TtsManagerTest {

    private lateinit var context: Context
    private lateinit var manager: TtsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = TtsManager(context)
    }

    @Test
    fun supportedSpeechRates_containsRequiredSteps() {
        val expected = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        assertEquals(expected, TtsManager.SUPPORTED_SPEECH_RATES)
    }

    @Test
    fun setSpeechRate_clampsWithinSafeRange() {
        manager.setSpeechRate(0.2f)
        assertEquals(TtsManager.MIN_SPEECH_RATE, manager.speechRate.value, 0.01f)

        manager.setSpeechRate(3.0f)
        assertEquals(TtsManager.MAX_SPEECH_RATE, manager.speechRate.value, 0.01f)

        manager.setSpeechRate(1.25f)
        assertEquals(1.25f, manager.speechRate.value, 0.01f)
    }

    @Test
    fun play_recordsTitlesAndLastPlayedText() {
        manager.play(
            text = "Article plain text content for speech",
            title = "Breaking News",
            subtitle = "TechCrunch",
        )

        assertEquals("Breaking News", manager.currentTitle)
        assertEquals("TechCrunch", manager.currentSubtitle)
        assertEquals("Article plain text content for speech", manager.lastPlayedText)
    }

    @Test
    fun stop_setsStateToIdle() {
        manager.stop()
        assertEquals(TtsState.IDLE, manager.ttsState.value)
    }

    @Test
    fun pause_preservesLastPlayedText_forResume() {
        manager.play("Paragraph text", "Title", "Feed")
        manager.pause()

        assertEquals(TtsState.IDLE, manager.ttsState.value)
        assertEquals("Paragraph text", manager.lastPlayedText)
        assertEquals("Title", manager.currentTitle)
    }

    /** Robolectric tạo TextToSpeech nhưng không gọi onInit; gọi tay để cài utterance listener. */
    private fun initializedListener(): UtteranceProgressListener {
        manager.onInit(TextToSpeech.SUCCESS)
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotNull("Robolectric phải tạo TextToSpeech", tts)
        return requireNotNull(shadowOf(tts).utteranceProgressListener)
    }

    @Test
    fun completions_emitsOnceForSingleChunkText() = runTest {
        val listener = initializedListener()
        val emitted = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.completions.collect { emitted += it } }

        manager.play("short text", "Title", "Feed")
        listener.onDone("TTS_ID")

        assertEquals(1, emitted.size)
    }

    @Test
    fun completions_emitsOnlyForLastChunkOfLongText() = runTest {
        val listener = initializedListener()
        val emitted = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.completions.collect { emitted += it } }
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        val text = "x".repeat(maxLength * 2)
        val lastIndex = text.chunked(maxLength - 100).lastIndex
        assertTrue("văn bản dài phải bị chia ít nhất 3 đoạn", lastIndex >= 2)

        manager.play(text, "Title", "Feed")
        listener.onDone("TTS_ID_0")
        assertEquals("đoạn đầu chưa phải đoạn cuối", 0, emitted.size)

        listener.onDone("TTS_ID_$lastIndex")
        assertEquals(1, emitted.size)
    }

    @Test
    fun completions_doesNotEmitAfterPause() = runTest {
        val listener = initializedListener()
        val emitted = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.completions.collect { emitted += it } }

        manager.play("short text", "Title", "Feed")
        manager.pause()
        listener.onDone("TTS_ID")

        assertEquals(0, emitted.size)
    }

    @Test
    fun completions_doesNotEmitAfterStop() = runTest {
        val listener = initializedListener()
        val emitted = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.completions.collect { emitted += it } }

        manager.play("short text", "Title", "Feed")
        manager.stop()
        listener.onDone("TTS_ID")

        assertEquals(0, emitted.size)
    }

    @Test
    fun completions_doesNotEmitForUnknownUtteranceId() = runTest {
        val listener = initializedListener()
        val emitted = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.completions.collect { emitted += it } }

        manager.play("short text", "Title", "Feed")
        listener.onDone("OTHER")

        assertEquals(0, emitted.size)
    }

    @Test
    fun ttsState_stillReturnsToIdleOnDone() {
        val listener = initializedListener()

        manager.play("short text", "Title", "Feed")
        assertEquals(TtsState.PLAYING, manager.ttsState.value)
        listener.onDone("TTS_ID")

        assertEquals(TtsState.IDLE, manager.ttsState.value)
    }

    @Test
    fun speakNotice_speaksWithoutOverwritingResumeState() {
        initializedListener()
        manager.play("Article body", "Title", "Feed")

        manager.speakNotice("No more new articles")

        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertEquals("No more new articles", shadowOf(tts).lastSpokenText)
        assertEquals("Article body", manager.lastPlayedText)
        assertEquals("Title", manager.currentTitle)
    }

    @Test
    fun speakNotice_doesNotEmitArticleCompletion() = runTest {
        val listener = initializedListener()
        val emitted = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.completions.collect { emitted += it } }
        manager.play("Article body", "Title", "Feed")

        manager.speakNotice("No new articles")
        listener.onDone("TTS_NOTICE")

        assertEquals(0, emitted.size)
    }

    @Test
    fun play_beforeInit_isQueuedAndSpokenOnceTheEngineIsReady() {
        // Khởi động nguội (deep link / Assistant): play() chạy trước khi onInit được gọi.
        manager.play("Early article", "Early title", "Early feed")
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertEquals("chưa init thì chưa nói được", null, shadowOf(tts).lastSpokenText)

        manager.onInit(TextToSpeech.SUCCESS)

        assertEquals("Early article", shadowOf(tts).lastSpokenText)
        assertEquals(TtsState.PLAYING, manager.ttsState.value)
    }

    @Test
    fun play_beforeInit_keepsOnlyTheLatestRequest() {
        manager.play("First", "T1", "F1")
        manager.play("Second", "T2", "F2")

        manager.onInit(TextToSpeech.SUCCESS)

        assertEquals("Second", shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
    }

    @Test
    fun stop_beforeInit_cancelsThePendingRequest() {
        manager.play("Pending", "T", "F")
        manager.stop()

        manager.onInit(TextToSpeech.SUCCESS)

        assertEquals(null, shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
    }

    @Test
    fun pause_beforeInit_cancelsThePendingRequest() {
        manager.play("Pending", "T", "F")
        manager.pause()

        manager.onInit(TextToSpeech.SUCCESS)

        assertEquals(null, shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
    }

    @Test
    fun speakNotice_beforeInit_isSpokenOnceTheEngineIsReady() {
        manager.speakNotice("No new articles")

        manager.onInit(TextToSpeech.SUCCESS)

        assertEquals("No new articles", shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
    }

    @Test
    fun onInitFailure_dropsPendingRequestAndReportsError() {
        manager.play("Pending", "T", "F")

        manager.onInit(TextToSpeech.ERROR)

        assertEquals(TtsState.PLAY_ERROR, manager.ttsState.value)
    }

    @Test
    fun play_invokesOnPlayRequestedFirst_evenBeforeInit() {
        var calls = 0
        manager.onPlayRequested = { calls++ }

        manager.play("Text", "T", "F")
        assertEquals(1, calls)

        manager.onInit(TextToSpeech.SUCCESS)
        assertEquals(2, calls)
    }

    @Test
    fun pauseStopAndNotice_doNotInvokeOnPlayRequested() {
        var calls = 0
        manager.onPlayRequested = { calls++ }
        manager.onInit(TextToSpeech.SUCCESS)

        manager.pause()
        manager.stop()
        manager.speakNotice("Notice")

        assertEquals(0, calls)
    }
}
