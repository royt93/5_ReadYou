package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A TTS error paused the player but never gave audio focus back, so music or video in another app stayed ducked until the process died.
 * Robolectric's AudioManager records the focus requests, which is what this reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CommuteAudioFocusOnErrorTest {
    private lateinit var player: CommuteAudioPlayer
    private lateinit var audioManager: AudioManager

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        player = CommuteAudioPlayer(context)
    }

    @Test
    fun anUtteranceErrorGivesAudioFocusBack() {
        player.handleUtteranceStart("COMMUTE_LINE_0")
        val shadow = shadowOf(audioManager)
        assertNotNull("focus is held while a line is spoken", shadow.lastAudioFocusRequest)
        assertFalse("nothing abandoned yet", shadow.lastAbandonedAudioFocusRequest != null)

        player.handleUtteranceError("COMMUTE_LINE_0", 1)

        assertNotNull("the focus request must be abandoned after an error", shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun theErrorAlsoStopsReportingPlayback() {
        player.handleUtteranceStart("COMMUTE_LINE_0")
        player.handleUtteranceError("COMMUTE_LINE_0", 1)
        assertFalse(player.playerState.value.isPlaying)
    }

    @Test
    fun anErrorBeforeAnyFocusRequestDoesNotThrow() {
        player.handleUtteranceError(null, null)
        assertNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }
}
