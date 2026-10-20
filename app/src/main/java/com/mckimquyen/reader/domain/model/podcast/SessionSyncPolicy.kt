package com.mckimquyen.reader.domain.model.podcast

import kotlin.math.abs

/**
 * Tells the media session when its playback state really needs refreshing. While playing, clients extrapolate the
 * position from the last update and the speed, so a new state is only needed on a status/speed change or a jump.
 */
class SessionSyncPolicy(private val driftToleranceSec: Int = DRIFT_TOLERANCE_SEC) {

    private data class Snapshot(val status: PodcastStatus, val speed: Float, val positionSec: Int, val atMs: Long)

    private var last: Snapshot? = null

    fun needsUpdate(status: PodcastStatus, speed: Float, positionSec: Int, nowMs: Long): Boolean {
        val previous = last
        val needs = when {
            previous == null || previous.status != status || previous.speed != speed -> true
            status == PodcastStatus.PLAYING -> {
                val elapsedSec = ((nowMs - previous.atMs) / MILLIS_PER_SECOND * speed).toInt()
                abs(positionSec - (previous.positionSec + elapsedSec)) > driftToleranceSec
            }
            else -> positionSec != previous.positionSec
        }
        if (needs) last = Snapshot(status, speed, positionSec, nowMs)
        return needs
    }

    companion object {
        const val DRIFT_TOLERANCE_SEC = 2
        private const val MILLIS_PER_SECOND = 1_000f
    }
}
