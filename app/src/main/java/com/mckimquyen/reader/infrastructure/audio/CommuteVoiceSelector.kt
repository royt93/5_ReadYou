package com.mckimquyen.reader.infrastructure.audio

import android.speech.tts.Voice
import java.util.Locale

/** Whether the engine could offer two genuinely different voices for Alex and Sam, or only one. */
enum class CommuteVoiceMode { REAL_DUAL, SIMULATED }

/** Two distinct [Voice]s to speak with, or null for a speaker the device could not give one to. */
data class CommuteVoiceAssignment(
    val mode: CommuteVoiceMode,
    val alex: Voice?,
    val sam: Voice?,
)

/**
 * Picks two genuinely different [Voice]s for the two hosts, or honestly reports that the device
 * cannot offer that.
 *
 * Android's TTS API has no notion of a voice's gender, so this never promises "a man's voice and a
 * woman's voice" — only "two different voices". Whatever the previous pitch/rate trick's comments
 * claimed, that is the only thing this API can actually deliver.
 */
object CommuteVoiceSelector {

    /**
     * @param voices every voice the engine reports, typically `TextToSpeech.getVoices()`.
     * @param targetLocale the language the episode is spoken in; a voice for another language would
     *   mispronounce it entirely.
     */
    fun select(voices: Set<Voice>, targetLocale: Locale): CommuteVoiceAssignment {
        val candidates = voices
            // A voice that needs a network round-trip must not be relied on: losing connectivity
            // mid-episode would silently break speech that used to work perfectly well offline.
            .filterNot { it.isNetworkConnectionRequired }
            .filter { it.locale.language == targetLocale.language }
            // Sorting by name makes the pick deterministic: the same device reports the same
            // two voices every time instead of one that depends on Set iteration order.
            .sortedBy { it.name }

        val distinct = candidates.distinctBy { it.name }
        if (distinct.size < 2) {
            return CommuteVoiceAssignment(mode = CommuteVoiceMode.SIMULATED, alex = null, sam = null)
        }

        return CommuteVoiceAssignment(
            mode = CommuteVoiceMode.REAL_DUAL,
            alex = distinct[0],
            sam = distinct[1],
        )
    }
}
