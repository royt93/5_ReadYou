package com.mckimquyen.reader.infrastructure.audio.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ZenSoundSynthesizerTest {

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
}
