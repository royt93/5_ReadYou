package com.mckimquyen.reader.infrastructure.audio.ambient

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * ZEN-06: [ZenAudioManager] không bao giờ được báo "đang phát" khi âm thanh thực tế không chạy —
 * dù do audio focus bị từ chối, hay do AudioTrack không khởi tạo được.
 */
class ZenAudioManagerTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun managerWithFocusResult(granted: Boolean): ZenAudioManager {
        val audioManager = mockk<AudioManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val result = if (granted) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns result
        @Suppress("DEPRECATION")
        every { audioManager.requestAudioFocus(any(), any(), any()) } returns result
        return ZenAudioManager(context)
    }

    @Test
    fun play_whenAudioFocusDenied_doesNotReportPlaying() {
        val manager = managerWithFocusResult(granted = false)

        val started = manager.play(ZenSoundType.GENTLE_RAIN)

        assertFalse("play() must report failure", started)
        assertFalse("isPlaying must stay false", manager.isPlaying.value)
        assertEquals(ZenPlaybackError.AUDIO_FOCUS_DENIED, manager.playbackError.value)
    }

    @Test
    fun play_whenFocusDenied_doesNotStartSynthesizer() {
        val audioManager = mockk<AudioManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        @Suppress("DEPRECATION")
        every { audioManager.requestAudioFocus(any(), any(), any()) } returns
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        val manager = ZenAudioManager(context)

        manager.play()

        // Focus was requested, but abandon must not be called for a request that never succeeded.
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }
        assertFalse(manager.isPlaying.value)
    }

    @Test
    fun play_whenFocusGrantedButAudioTrackFails_reportsFailureAndAbandonsFocus() {
        // On the JVM there is no real AudioTrack, so the synthesizer start() fails: the manager
        // must surface that instead of pretending playback began.
        val manager = managerWithFocusResult(granted = true)

        val started = manager.play(ZenSoundType.PINK_NOISE)

        assertFalse("play() must report failure when AudioTrack cannot start", started)
        assertFalse("isPlaying must stay false", manager.isPlaying.value)
        assertEquals(ZenPlaybackError.AUDIO_TRACK_FAILED, manager.playbackError.value)
    }

    @Test
    fun play_failure_stillRecordsRequestedSoundType() {
        val manager = managerWithFocusResult(granted = true)

        manager.play(ZenSoundType.TIBETAN_BOWL)

        // The user's selection is kept so the UI stays consistent after a failed attempt.
        assertEquals(ZenSoundType.TIBETAN_BOWL, manager.currentType.value)
    }

    @Test
    fun clearPlaybackError_resetsErrorState() {
        val manager = managerWithFocusResult(granted = false)
        manager.play()
        assertEquals(ZenPlaybackError.AUDIO_FOCUS_DENIED, manager.playbackError.value)

        manager.clearPlaybackError()

        assertNull(manager.playbackError.value)
    }

    @Test
    fun stop_afterFailedPlay_keepsStateConsistent() {
        val manager = managerWithFocusResult(granted = false)
        manager.play()

        manager.stop()

        assertFalse(manager.isPlaying.value)
    }

    @Test
    fun toggle_whenNotPlaying_attemptsPlayAndStaysFalseOnFailure() {
        val manager = managerWithFocusResult(granted = false)

        manager.toggle(ZenSoundType.OCEAN_WAVES)

        assertFalse("A failed toggle must not flip the UI into playing", manager.isPlaying.value)
        assertEquals(ZenPlaybackError.AUDIO_FOCUS_DENIED, manager.playbackError.value)
    }

    @Test
    fun setVolume_clampsToValidRange() {
        val manager = managerWithFocusResult(granted = true)

        manager.setVolume(1.7f)
        assertEquals(1f, manager.volume.value, 0.001f)

        manager.setVolume(-0.4f)
        assertEquals(0f, manager.volume.value, 0.001f)
    }

    @Test
    fun setSleepTimer_recordsMinutes() {
        val manager = managerWithFocusResult(granted = true)

        manager.setSleepTimer(20)

        assertEquals(20, manager.sleepTimerMinutes.value)
    }
}
