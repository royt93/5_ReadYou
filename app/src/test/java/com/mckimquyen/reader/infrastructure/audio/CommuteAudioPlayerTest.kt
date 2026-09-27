package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.test.runTest
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
        assertFalse(state.isCompleted)
        assertEquals("Line 0", state.currentDialogue?.text)
        // isPlaying is deliberately not asserted true here: only the engine's onStart callback can
        // report that speech actually began. Claiming it up front is what used to leave the UI
        // showing "playing" over silence when the engine was not ready.
    }

    @Test
    fun pause_stopsPlaybackAndDropsAnyQueuedRequest() {
        player.playEpisode(sampleEpisode, startFromIndex = 0)
        player.pause()

        val state = player.playerState.value
        assertFalse(state.isPlaying)
        assertFalse("Pausing must not leave a request that fires later", state.isAwaitingPlayback)
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
        assertFalse(state.isAwaitingPlayback)
    }

    @Test
    fun prepareEpisode_loadsTheEpisodeWithoutSpeaking() {
        player.prepareEpisode(sampleEpisode)
        val state = player.playerState.value

        assertEquals(sampleEpisode, state.episode)
        assertEquals(0, state.currentDialogueIndex)
        assertFalse("Preparing must not start playback", state.isPlaying)
        assertFalse(state.isCompleted)
        assertFalse(state.isAwaitingPlayback)
    }

    @Test
    fun playRequestBeforeTheEngineIsReady_isQueuedRatherThanDropped() {
        // Robolectric's TTS shadow never reports a successful onInit, so this is the real
        // "engine not ready" path: the request used to vanish without a trace.
        player.playEpisode(sampleEpisode, startFromIndex = 0)
        val state = player.playerState.value

        assertTrue("The waiting request must be visible to the UI", state.isAwaitingPlayback)
        assertFalse("Nothing is audible yet, so isPlaying must stay false", state.isPlaying)
        assertEquals(sampleEpisode, state.episode)
    }

    @Test
    fun ttsInitFailure_reportsNotReadyAndClearsTheQueuedRequest() {
        player.playEpisode(sampleEpisode, startFromIndex = 0)
        assertTrue(player.playerState.value.isAwaitingPlayback)

        player.onInit(TextToSpeech.ERROR)

        val state = player.playerState.value
        assertFalse(state.isTtsReady)
        assertFalse("A dead engine must not leave a request pending forever", state.isAwaitingPlayback)
    }

    @Test
    fun awaitReady_givesUpAfterTheTimeoutWhenTheEngineNeverInitialises() = runTest {
        // The morning worker relies on this: a false here is what stops the notification going out.
        assertFalse(player.awaitReady(timeoutMs = 50L))
    }

    @Test
    fun aPlayerWithoutTwoDeviceVoices_reportsSimulatedModeHonestly() {
        // Robolectric exposes no installed TTS voices. That is the same contract as a real device
        // with fewer than two suitable offline voices: it must never claim REAL_DUAL.
        player.onInit(TextToSpeech.SUCCESS)

        assertEquals(CommuteVoiceMode.SIMULATED, player.playerState.value.voiceMode)
    }

    @Test
    fun shutdown_reportsTheEngineAsNoLongerReadyAndDropsItsVoiceAssignment() {
        player.shutdown()
        val state = player.playerState.value

        assertFalse(state.isTtsReady)
        assertFalse(state.isPlaying)
        assertFalse(state.isAwaitingPlayback)
        assertEquals(CommuteVoiceMode.SIMULATED, state.voiceMode)
    }

    @Test
    fun handleUtteranceStart_startsAmbientLoopAndDucksVolume() {
        player.prepareEpisode(sampleEpisode)
        player.handleUtteranceStart("COMMUTE_LINE_0")

        assertTrue("Player state must indicate isPlaying", player.playerState.value.isPlaying)
    }

    @Test
    fun handleUtteranceDone_restoresAmbientLoop() {
        player.prepareEpisode(sampleEpisode)
        player.handleUtteranceStart("COMMUTE_LINE_0")
        player.handleUtteranceDone("COMMUTE_LINE_0")

        assertTrue("Player state must still be playing until completed", player.playerState.value.isPlaying)
    }

    @Test
    fun pause_pausesAmbientLoop() {
        player.prepareEpisode(sampleEpisode)
        player.handleUtteranceStart("COMMUTE_LINE_0")
        player.pause()

        assertFalse(player.playerState.value.isPlaying)
        assertFalse(player.ambientLoop.isPlaying)
    }

    @Test
    fun stopAndReset_stopsAmbientLoop() {
        player.prepareEpisode(sampleEpisode)
        player.handleUtteranceStart("COMMUTE_LINE_0")
        player.stopAndReset()

        assertFalse(player.playerState.value.isPlaying)
        assertFalse(player.ambientLoop.isPlaying)
    }
}
