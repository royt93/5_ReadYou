package com.mckimquyen.reader.domain.sv

/** What the podcast controller needs from CommuteCast, so the two engines yield to each other without a direct dependency. */
interface CommuteGate {
    /** True while CommuteCast is speaking or a start is queued behind the TTS engine. */
    val isPlaying: Boolean
    fun pause()
    fun setOnPlayRequested(callback: (() -> Unit)?)
}
