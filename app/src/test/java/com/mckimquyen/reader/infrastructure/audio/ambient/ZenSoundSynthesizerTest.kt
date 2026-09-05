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
    fun synthesizer_volumeAndTypeSetting_doesNotCrash() {
        val synth = ZenSoundSynthesizer()
        synth.setVolume(0.8f)
        synth.setVolume(-0.5f) // Should clamp
        synth.setVolume(1.5f)  // Should clamp
        synth.setSoundType(ZenSoundType.OCEAN_WAVES)
        synth.stop() // Safe to call when not playing
    }
}
