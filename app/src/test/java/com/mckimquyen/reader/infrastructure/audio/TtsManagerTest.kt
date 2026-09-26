package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
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
}
