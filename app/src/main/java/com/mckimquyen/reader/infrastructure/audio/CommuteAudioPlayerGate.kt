package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.sv.CommuteGate

class CommuteAudioPlayerGate(private val player: CommuteAudioPlayer) : CommuteGate {
    override val isPlaying: Boolean
        get() = player.playerState.value.let { it.isPlaying || it.isAwaitingPlayback }

    override fun pause() = player.pause()

    override fun setOnPlayRequested(callback: (() -> Unit)?) {
        player.onPlayRequested = callback
    }
}
