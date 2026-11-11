package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteAudioPlayerGateTest {

    private lateinit var player: CommuteAudioPlayer
    private lateinit var gate: CommuteAudioPlayerGate
    private val episode = CommuteEpisode("e", "T", Date(0), listOf(CommuteDialogue(CommuteSpeaker.ALEX, "Hi")))

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        player = CommuteAudioPlayer(context)
        gate = CommuteAudioPlayerGate(player)
    }

    @Test
    fun isPlaying_isFalseWhenIdle_andTrueWhileAStartIsPending() {
        assertFalse(gate.isPlaying)

        player.playEpisode(episode, 0) // engine not ready in Robolectric: queued, isAwaitingPlayback = true

        assertTrue("a queued start must still count, or podcast would not yield", gate.isPlaying)
    }

    @Test
    fun pause_cancelsAQueuedStart() {
        player.playEpisode(episode, 0)

        gate.pause()

        assertFalse(gate.isPlaying)
    }

    @Test
    fun setOnPlayRequested_isTheCallbackThePlayerInvokes() {
        var asked = 0
        gate.setOnPlayRequested { asked++ }
        player.playEpisode(episode, 0)
        assertEquals(1, asked)

        gate.setOnPlayRequested(null)
        assertNull(player.onPlayRequested)
    }
}
