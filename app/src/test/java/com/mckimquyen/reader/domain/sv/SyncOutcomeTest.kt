package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOutcomeTest {
    @Test fun noFeedAtAllIsNotAFailure() = assertFalse(SyncOutcome.allFailed(0, 0))
    @Test fun someFeedsFetchedIsNotAFailure() = assertFalse(SyncOutcome.allFailed(5, 2))
    @Test fun everyFeedFailingIsAFailure() = assertTrue(SyncOutcome.allFailed(5, 0))
    @Test fun oneFeedThatFailsIsAFailure() = assertTrue(SyncOutcome.allFailed(1, 0))
}
