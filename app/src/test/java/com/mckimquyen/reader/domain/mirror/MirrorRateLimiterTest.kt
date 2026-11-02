package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRateLimiterTest {
    private var now = 0L
    private val limiter = MirrorRateLimiter { now }

    @Test fun allowsExactlyTheLimitPerMinuteThenRejects() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { assertTrue("request $it", limiter.tryRequest("10.0.0.2")) }
        assertFalse(limiter.tryRequest("10.0.0.2"))
    }

    @Test fun windowSlides() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { limiter.tryRequest("10.0.0.2") }
        now = MirrorLimits.RATE_WINDOW_MILLIS - 1
        assertFalse(limiter.tryRequest("10.0.0.2"))
        now = MirrorLimits.RATE_WINDOW_MILLIS
        assertTrue(limiter.tryRequest("10.0.0.2"))
    }

    @Test fun rejectedRequestsDoNotExtendTheBlock() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { limiter.tryRequest("10.0.0.2") }
        now = 30_000
        repeat(50) { limiter.tryRequest("10.0.0.2") }
        now = MirrorLimits.RATE_WINDOW_MILLIS
        assertTrue(limiter.tryRequest("10.0.0.2"))
    }

    @Test fun ipsAreIndependent() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { limiter.tryRequest("10.0.0.2") }
        assertTrue(limiter.tryRequest("10.0.0.3"))
    }

    @Test fun streamsAreCappedAndFreedOnClose() {
        repeat(MirrorLimits.MAX_CLIENTS) { assertTrue(limiter.tryOpenStream()) }
        assertFalse(limiter.tryOpenStream())
        limiter.closeStream()
        assertTrue(limiter.tryOpenStream())
    }

    @Test fun closingMoreThanOpenedNeverGoesNegative() {
        repeat(5) { limiter.closeStream() }
        repeat(MirrorLimits.MAX_CLIENTS) { assertTrue(limiter.tryOpenStream()) }
        assertFalse(limiter.tryOpenStream())
    }

    @Test fun trackedIpsStayBoundedWhenManyAddressesAppear() {
        repeat(1_000) { limiter.tryRequest("10.1.${it / 250}.${it % 250}") }
        assertTrue(limiter.trackedIps() <= MirrorLimits.MAX_TRACKED_IPS)
    }
}
