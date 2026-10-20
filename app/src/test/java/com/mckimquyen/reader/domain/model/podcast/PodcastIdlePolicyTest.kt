package com.mckimquyen.reader.domain.model.podcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastIdlePolicyTest {
    private var now = 0L
    private val policy = PodcastIdlePolicy(timeoutMs = 1_000L) { now }

    @Test fun idleStopsImmediately() = assertTrue(policy.shouldStop(PodcastStatus.IDLE))

    @Test fun playingAndPreparingNeverStop() {
        assertFalse(policy.shouldStop(PodcastStatus.PLAYING))
        now += 10_000
        assertFalse(policy.shouldStop(PodcastStatus.PREPARING))
    }

    @Test fun pausedStopsOnlyAfterTimeout() {
        assertFalse(policy.shouldStop(PodcastStatus.PAUSED))
        now += 999
        assertFalse(policy.shouldStop(PodcastStatus.PAUSED))
        now += 1
        assertTrue(policy.shouldStop(PodcastStatus.PAUSED))
    }

    @Test fun errorAlsoStopsAfterTimeout() {
        assertFalse(policy.shouldStop(PodcastStatus.ERROR))
        now += 1_000
        assertTrue(policy.shouldStop(PodcastStatus.ERROR))
    }

    @Test fun playingAgainRestartsTheClock() {
        policy.shouldStop(PodcastStatus.PAUSED)
        now += 900
        policy.shouldStop(PodcastStatus.PLAYING)
        now += 900
        assertFalse(policy.shouldStop(PodcastStatus.PAUSED))
        now += 999
        assertFalse(policy.shouldStop(PodcastStatus.PAUSED))
        now += 1
        assertTrue(policy.shouldStop(PodcastStatus.PAUSED))
    }
}
