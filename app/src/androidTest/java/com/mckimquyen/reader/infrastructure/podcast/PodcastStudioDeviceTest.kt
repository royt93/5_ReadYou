package com.mckimquyen.reader.infrastructure.podcast

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.infrastructure.audio.CommuteVoiceMode
import com.mckimquyen.reader.infrastructure.audio.CommuteVoiceSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs on live Android runtime to verify TextToSpeech voice discovery and deterministic selection.
 * Free of MockK: uses only the real Android TTS engine and pure CommuteVoiceSelector logic.
 */
@RunWith(AndroidJUnit4::class)
class PodcastStudioDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun deviceTtsInitialisesAndResolvesVoiceModeDeterminedByActualVoices() {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { initStatus ->
            status = initStatus
            ready.countDown()
        }
        try {
            assertTrue("TTS engine did not initialise in time", ready.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, status)
            val voices = engine.voices.orEmpty()
            assertNotNull(voices)

            val assignment = CommuteVoiceSelector.select(voices, Locale.getDefault())
            Log.i(TAG, "voices=${voices.size} mode=${assignment.mode} alex=${assignment.alex?.name} sam=${assignment.sam?.name}")

            // The badge shown to the user must reflect what the device really provides.
            if (assignment.mode == CommuteVoiceMode.REAL_DUAL) {
                assertNotNull(assignment.alex)
                assertNotNull(assignment.sam)
                assertTrue("Dual voices must be distinct", assignment.alex?.name != assignment.sam?.name)
            } else {
                assertEquals(null, assignment.alex)
                assertEquals(null, assignment.sam)
            }
        } finally {
            engine?.shutdown()
        }
    }

    private companion object {
        const val TAG = "PodcastStudioDeviceTest"
    }
}
