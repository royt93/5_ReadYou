package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCompletionGateTest {
    private val gate = ReadingCompletionGate()

    @Test
    fun bodyProgress_belowEightyPercent_doesNotComplete() {
        gate.onArticleChanged("a1", 0L)
        gate.onForegroundChanged(true, 0L)
        assertFalse(gate.isComplete("a1", lastVisibleBodyIndex = 78, bodyStartIndex = 10, bodyItemCount = 100, nowMillis = 30_000L))
    }

    @Test
    fun bodyProgress_exactlyEightyPercent_completesOnlyAfterThirtySeconds() {
        gate.onArticleChanged("a1", 0L)
        gate.onForegroundChanged(true, 0L)
        assertFalse(gate.isComplete("a1", 89, 10, 100, 29_999L))
        assertTrue(gate.isComplete("a1", 89, 10, 100, 30_000L))
    }

    @Test
    fun foregroundTime_doesNotAccumulateWhileAppIsBackgrounded() {
        gate.onArticleChanged("a1", 0L)
        gate.onForegroundChanged(true, 0L)
        assertFalse(gate.isComplete("a1", 99, 0, 100, 20_000L))
        gate.onForegroundChanged(false, 20_000L)
        gate.onForegroundChanged(true, 300_000L)
        assertFalse(gate.isComplete("a1", 99, 0, 100, 309_999L))
        assertTrue(gate.isComplete("a1", 99, 0, 100, 310_000L))
    }

    @Test
    fun changingArticles_resetsContinuousDwellTime() {
        gate.onArticleChanged("a1", 0L)
        gate.onForegroundChanged(true, 0L)
        gate.isComplete("a1", 99, 0, 100, 25_000L)
        gate.onArticleChanged("a2", 25_000L)
        assertFalse(gate.isComplete("a2", 99, 0, 100, 54_999L))
        assertTrue(gate.isComplete("a2", 99, 0, 100, 55_000L))
    }

    @Test
    fun unrelatedItemsBeforeAndAfterBody_doNotAffectBodyProgress() {
        gate.onArticleChanged("a1", 0L)
        gate.onForegroundChanged(true, 0L)
        // 20 metadata rows before body; body range [20, 120); quiz/ad/trailing rows after it.
        assertFalse(gate.isComplete("a1", lastVisibleBodyIndex = 98, bodyStartIndex = 20, bodyItemCount = 100, nowMillis = 30_000L))
        assertTrue(gate.isComplete("a1", lastVisibleBodyIndex = 99, bodyStartIndex = 20, bodyItemCount = 100, nowMillis = 30_000L))
    }

    @Test
    fun emptyBody_invalidRangeAndWrongArticleNeverComplete() {
        gate.onArticleChanged("a1", 0L)
        gate.onForegroundChanged(true, 0L)
        assertFalse(gate.isComplete("a1", 0, 0, 0, 30_000L))
        assertFalse(gate.isComplete("a1", 10, 10, 0, 30_000L))
        assertFalse(gate.isComplete("other", 99, 0, 100, 30_000L))
        assertFalse(gate.isComplete("a1", 9, 10, 100, 30_000L))
    }

    @Test
    fun oneCompletion_isReportedOnlyOnceForAnArticle() {
        gate.onArticleChanged("a1", 0L)
        gate.onForegroundChanged(true, 0L)
        assertTrue(gate.isComplete("a1", 99, 0, 100, 30_000L))
        assertFalse("a completion is emitted only once per article visit", gate.isComplete("a1", 99, 0, 100, 31_000L))
    }
}
