package com.mckimquyen.reader.domain.sv

/** What the podcast controller needs from text-to-speech, so it does not depend on TtsManager directly. */
interface TtsGate {
    val isSpeaking: Boolean
    fun stop()
    fun setOnPlayRequested(callback: (() -> Unit)?)
}
