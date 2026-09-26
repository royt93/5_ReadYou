package com.mckimquyen.reader.infrastructure.audio.ambient

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ZenSoundSynthesizerTest {

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

    @Test
    fun zenSoundType_hasAllExpectedPresets() {
        val types = ZenSoundType.values()
        assertEquals(5, types.size)
        assertNotNull(ZenSoundType.valueOf("GENTLE_RAIN"))
        assertNotNull(ZenSoundType.valueOf("OCEAN_WAVES"))
        assertNotNull(ZenSoundType.valueOf("PINK_NOISE"))
        assertNotNull(ZenSoundType.valueOf("BINAURAL_40HZ"))
        assertNotNull(ZenSoundType.valueOf("TIBETAN_BOWL"))
    }

    @Test
    fun synthesizer_volumeAndTypeSetting_clampsAndAssignsCorrectly() {
        val synth = ZenSoundSynthesizer()
        assertEquals(0.5f, synth.currentVolume, 0.001f)
        assertEquals(false, synth.isCurrentlyPlaying)

        synth.setVolume(0.8f)
        assertEquals(0.8f, synth.currentVolume, 0.001f)

        synth.setVolume(-0.5f) // Should clamp to 0.0f
        assertEquals(0.0f, synth.currentVolume, 0.001f)

        synth.setVolume(1.5f)  // Should clamp to 1.0f
        assertEquals(1.0f, synth.currentVolume, 0.001f)

        synth.setSoundType(ZenSoundType.OCEAN_WAVES)
        assertEquals(ZenSoundType.OCEAN_WAVES, synth.activeSoundType)

        // Multiple idempotent stop calls should not throw
        synth.stop()
        synth.stop()
        assertEquals(false, synth.isCurrentlyPlaying)
    }

    // ---- ZEN-06: state must never claim "playing" when audio is actually silent ----

    @Test
    fun start_whenAudioTrackCannotInitialize_reportsFailureAndStaysStopped() {
        // Plain JVM unit test: AudioTrack is not available, so start() must fail cleanly
        // instead of leaving isCurrentlyPlaying stuck at true.
        val synth = ZenSoundSynthesizer()

        val started = synth.start(ZenSoundType.GENTLE_RAIN, 0.5f)

        assertEquals(false, started)
        assertEquals(false, synth.isCurrentlyPlaying)
    }

    @Test
    fun start_failure_doesNotInvokeUnexpectedStopCallback() {
        // An init failure is reported via the return value, not via onStoppedUnexpectedly,
        // so the manager does not double-handle the same failure.
        var unexpectedStops = 0
        val synth = ZenSoundSynthesizer(onStoppedUnexpectedly = { unexpectedStops++ })

        val started = synth.start(ZenSoundType.PINK_NOISE, 0.5f)

        assertEquals(false, started)
        assertEquals(0, unexpectedStops)
        assertEquals(false, synth.isCurrentlyPlaying)
    }

    @Test
    fun stop_afterFailedStart_isSafeAndKeepsStateFalse() {
        val synth = ZenSoundSynthesizer()
        synth.start(ZenSoundType.OCEAN_WAVES, 0.5f)

        synth.stop()
        synth.stop()

        assertEquals(false, synth.isCurrentlyPlaying)
    }

    @Test
    fun setVolumeAndType_afterFailedStart_stillApplyWithoutThrowing() {
        val synth = ZenSoundSynthesizer()
        synth.start(ZenSoundType.GENTLE_RAIN, 0.5f)

        synth.setVolume(0.42f)
        synth.setSoundType(ZenSoundType.TIBETAN_BOWL)

        assertEquals(0.42f, synth.currentVolume, 0.001f)
        assertEquals(ZenSoundType.TIBETAN_BOWL, synth.activeSoundType)
        assertEquals(false, synth.isCurrentlyPlaying)
    }
}
