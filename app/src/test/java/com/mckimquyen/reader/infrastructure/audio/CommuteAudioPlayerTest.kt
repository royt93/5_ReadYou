package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteAudioPlayerTest {

    private lateinit var context: Context
    private lateinit var player: CommuteAudioPlayer

    private val sampleEpisode = CommuteEpisode(
        id = "ep_1",
        title = "Morning Edition",
        date = Date(),
        dialogues = listOf(
            CommuteDialogue(CommuteSpeaker.ALEX, "Line 0"),
            CommuteDialogue(CommuteSpeaker.SAM, "Line 1"),
            CommuteDialogue(CommuteSpeaker.ALEX, "Line 2"),
        )
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = CommuteAudioPlayer(context)
    }

    @Test
    fun playEpisode_initializesStateCorrectly() {
        player.playEpisode(sampleEpisode, startFromIndex = 0)
        val state = player.playerState.value

        assertEquals(sampleEpisode, state.episode)
        assertEquals(0, state.currentDialogueIndex)
        assertTrue(state.isPlaying)
        assertFalse(state.isCompleted)
        assertEquals("Line 0", state.currentDialogue?.text)
    }

    @Test
    fun pauseAndResume_updatesPlayingState() {
        player.playEpisode(sampleEpisode, startFromIndex = 0)
        player.pause()
        assertFalse(player.playerState.value.isPlaying)

        player.resume()
        assertTrue(player.playerState.value.isPlaying)
    }

    @Test
    fun seekToDialogue_updatesIndex() {
        player.playEpisode(sampleEpisode, startFromIndex = 0)
        player.seekToDialogue(2)
        assertEquals(2, player.playerState.value.currentDialogueIndex)
        assertEquals("Line 2", player.playerState.value.currentDialogue?.text)
    }

    @Test
    fun unlockDeepDive_updatesState() {
        assertFalse(player.playerState.value.isDeepDiveUnlocked)
        player.unlockDeepDive()
        assertTrue(player.playerState.value.isDeepDiveUnlocked)
    }

    @Test
    fun stopAndReset_clearsState() {
        player.playEpisode(sampleEpisode, startFromIndex = 1)
        player.stopAndReset()
        val state = player.playerState.value

        assertFalse(state.isPlaying)
        assertEquals(0, state.currentDialogueIndex)
        assertFalse(state.isCompleted)
    }
}
