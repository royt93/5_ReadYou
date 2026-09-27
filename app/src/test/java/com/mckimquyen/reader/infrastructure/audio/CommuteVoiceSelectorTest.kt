package com.mckimquyen.reader.infrastructure.audio

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteVoiceSelectorTest {

    @Test
    fun `no voices falls back honestly to simulated mode`() {
        val result = CommuteVoiceSelector.select(emptySet(), Locale.ENGLISH)

        assertEquals(CommuteVoiceMode.SIMULATED, result.mode)
        assertNull(result.alex)
        assertNull(result.sam)
    }

    @Test
    fun `one voice is still one voice no matter how pitch is changed`() {
        val result = CommuteVoiceSelector.select(
            voices = setOf(voice("only", Locale.US)),
            targetLocale = Locale.US,
        )

        assertEquals(CommuteVoiceMode.SIMULATED, result.mode)
        assertNull(result.alex)
        assertNull(result.sam)
    }

    @Test
    fun `two offline voices in the episode language become real dual mode`() {
        val first = voice("en-a", Locale.US)
        val second = voice("en-b", Locale.UK)

        val result = CommuteVoiceSelector.select(setOf(second, first), Locale.ENGLISH)

        assertEquals(CommuteVoiceMode.REAL_DUAL, result.mode)
        assertEquals(first, result.alex)
        assertEquals(second, result.sam)
        assertNotEquals(result.alex?.name, result.sam?.name)
    }

    @Test
    fun `selection is deterministic regardless of Set iteration order`() {
        val alpha = voice("alpha", Locale.US)
        val beta = voice("beta", Locale.US)
        val gamma = voice("gamma", Locale.US)

        val forward = CommuteVoiceSelector.select(linkedSetOf(gamma, alpha, beta), Locale.US)
        val reverse = CommuteVoiceSelector.select(linkedSetOf(beta, gamma, alpha), Locale.US)

        assertEquals("alpha", forward.alex?.name)
        assertEquals("beta", forward.sam?.name)
        assertEquals(forward, reverse)
    }

    @Test
    fun `voices in another language are not picked just to reach a count of two`() {
        val english = voice("en-one", Locale.US)
        val french = voice("fr-one", Locale.FRANCE)

        val result = CommuteVoiceSelector.select(setOf(english, french), Locale.ENGLISH)

        assertEquals(CommuteVoiceMode.SIMULATED, result.mode)
    }

    @Test
    fun `network dependent voices are rejected so offline playback keeps working`() {
        val offline = voice("offline", Locale.US)
        val cloud = voice("cloud", Locale.US, needsNetwork = true)

        val result = CommuteVoiceSelector.select(setOf(offline, cloud), Locale.US)

        assertEquals(CommuteVoiceMode.SIMULATED, result.mode)
        assertNull(result.alex)
        assertNull(result.sam)
    }

    @Test
    fun `duplicate names do not masquerade as two different voices`() {
        val usCopy = voice("same-name", Locale.US)
        val ukCopy = voice("same-name", Locale.UK)

        val result = CommuteVoiceSelector.select(setOf(usCopy, ukCopy), Locale.ENGLISH)

        assertEquals(CommuteVoiceMode.SIMULATED, result.mode)
    }

    private fun voice(
        name: String,
        locale: Locale,
        needsNetwork: Boolean = false,
    ) = Voice(
        name,
        locale,
        Voice.QUALITY_NORMAL,
        Voice.LATENCY_NORMAL,
        needsNetwork,
        emptySet(),
    )
}
