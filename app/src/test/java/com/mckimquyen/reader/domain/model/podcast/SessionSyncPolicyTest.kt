package com.mckimquyen.reader.domain.model.podcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSyncPolicyTest {
    private val policy = SessionSyncPolicy()

    @Test fun firstCallAlwaysUpdates() = assertTrue(policy.needsUpdate(PodcastStatus.PLAYING, 1f, 0, 0))

    @Test fun steadyPlaybackDoesNotUpdate() {
        policy.needsUpdate(PodcastStatus.PLAYING, 1f, 10, 0)
        assertFalse(policy.needsUpdate(PodcastStatus.PLAYING, 1f, 11, 1_000))
        assertFalse(policy.needsUpdate(PodcastStatus.PLAYING, 1f, 12, 2_000))
    }

    @Test fun steadyPlaybackAtOneAndAHalfSpeedDoesNotUpdate() {
        policy.needsUpdate(PodcastStatus.PLAYING, 1.5f, 10, 0)
        assertFalse(policy.needsUpdate(PodcastStatus.PLAYING, 1.5f, 11, 1_000))
        assertFalse(policy.needsUpdate(PodcastStatus.PLAYING, 1.5f, 13, 2_000))
    }

    @Test fun statusChangeUpdates() {
        policy.needsUpdate(PodcastStatus.PLAYING, 1f, 10, 0)
        assertTrue(policy.needsUpdate(PodcastStatus.PAUSED, 1f, 10, 500))
    }

    @Test fun speedChangeUpdates() {
        policy.needsUpdate(PodcastStatus.PLAYING, 1f, 10, 0)
        assertTrue(policy.needsUpdate(PodcastStatus.PLAYING, 1.5f, 10, 500))
    }

    @Test fun seekJumpUpdates() {
        policy.needsUpdate(PodcastStatus.PLAYING, 1f, 10, 0)
        assertTrue(policy.needsUpdate(PodcastStatus.PLAYING, 1f, 25, 1_000))
        assertFalse(policy.needsUpdate(PodcastStatus.PLAYING, 1f, 26, 2_000))
    }

    @Test fun pausedPositionChangeUpdatesButSamePositionDoesNot() {
        policy.needsUpdate(PodcastStatus.PAUSED, 1f, 10, 0)
        assertFalse(policy.needsUpdate(PodcastStatus.PAUSED, 1f, 10, 5_000))
        assertTrue(policy.needsUpdate(PodcastStatus.PAUSED, 1f, 40, 6_000))
    }
}
