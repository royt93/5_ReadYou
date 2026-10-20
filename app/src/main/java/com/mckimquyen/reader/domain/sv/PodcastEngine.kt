package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.podcast.PodcastStatus

/** Audio engine port: the real one is an ExoPlayer inside the playback service; tests use a fake. Main thread only. */
interface PodcastEngine {
    fun prepare(url: String, startPositionSec: Int)
    fun play()
    fun pause()
    fun stop()
    fun seekToSec(sec: Int)
    fun setSpeed(speed: Float)
    fun positionSec(): Int
    fun status(): PodcastStatus
    fun durationSec(): Int
}

/** Starts the foreground playback service so it can attach an engine. */
fun interface PodcastServiceStarter {
    fun start()
}
