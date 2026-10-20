package com.mckimquyen.reader.domain.model.podcast

/** Decides when the foreground service should stop itself: right away when idle, after [timeoutMs] of pause/error. */
class PodcastIdlePolicy(
    private val timeoutMs: Long = IDLE_STOP_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var inactiveSince: Long? = null

    fun shouldStop(status: PodcastStatus): Boolean = when (status) {
        PodcastStatus.IDLE -> true
        PodcastStatus.PLAYING, PodcastStatus.PREPARING -> {
            inactiveSince = null
            false
        }
        PodcastStatus.PAUSED, PodcastStatus.ERROR -> {
            val since = inactiveSince ?: nowMillis().also { inactiveSince = it }
            nowMillis() - since >= timeoutMs
        }
    }

    companion object {
        const val IDLE_STOP_MS = 10 * 60 * 1_000L
    }
}
