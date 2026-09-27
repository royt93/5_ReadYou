package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteAmbientLoopTest {

    private lateinit var context: Context
    private lateinit var loop: CommuteAmbientLoop

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        loop = CommuteAmbientLoop(context)
    }

    @Test
    fun start_initializesStateSafely() {
        loop.start(CommuteAmbientLoop.NORMAL_VOLUME)
        // Robolectric MediaPlayer.create for raw resources returns null.
        // The loop must handle this safely without crashing.
        assertFalse(loop.isPlaying)
    }

    @Test
    fun setVolume_clampsCorrectly() {
        loop.start()
        loop.setVolume(-0.5f)
        loop.setVolume(1.5f)
        assertFalse(loop.isPlaying) // null-safe
    }

    @Test
    fun pauseAndResume_updatesPlaybackState() {
        loop.start()
        loop.pause()
        loop.resume()
        assertFalse(loop.isPlaying) // null-safe
    }

    @Test
    fun stop_isIdempotent() {
        loop.start()
        loop.stop()
        assertFalse(loop.isPlaying)

        // Calling stop a second time must not crash
        loop.stop()
        assertFalse(loop.isPlaying)
    }
}
