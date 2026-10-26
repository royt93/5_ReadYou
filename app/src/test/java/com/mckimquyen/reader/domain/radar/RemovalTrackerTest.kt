package com.mckimquyen.reader.domain.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalTrackerTest {

    private fun stored(link: String, date: Long?, missed: Int = 0, removed: Boolean = false) =
        StoredArticleState("id-$link", link, date, missed, removed)

    private fun run(
        stored: List<StoredArticleState>,
        present: Set<String>,
        oldest: Long? = 100L,
        valid: Boolean = true,
    ) = RemovalTracker.evaluate(valid, present, oldest, stored)

    @Test
    fun absentArticleInsideWindow_incrementsMissCount() {
        val u = run(listOf(stored("a", 200L)), setOf("other")).single()
        assertEquals(1, u.missedSyncs)
        assertEquals(false, u.removed)
    }

    @Test
    fun thirdConsecutiveMiss_marksRemoved() {
        val u = run(listOf(stored("a", 200L, missed = RemovalTracker.REMOVAL_THRESHOLD - 1)), setOf("other")).single()
        assertEquals(RemovalTracker.REMOVAL_THRESHOLD, u.missedSyncs)
        assertTrue(u.removed)
    }

    @Test
    fun articleOlderThanOldestFeedEntry_isIgnored() {
        assertTrue(run(listOf(stored("a", 50L)), setOf("other"), oldest = 100L).isEmpty())
    }

    @Test
    fun articleWithoutDate_isIgnored() {
        assertTrue(run(listOf(stored("a", null)), setOf("other")).isEmpty())
    }

    @Test
    fun invalidFetch_changesNothing() {
        assertTrue(run(listOf(stored("a", 200L, missed = 2)), emptySet(), valid = false).isEmpty())
    }

    @Test
    fun emptyFeed_changesNothing() {
        assertTrue(run(listOf(stored("a", 200L, missed = 2)), emptySet(), oldest = null).isEmpty())
    }

    @Test
    fun reappearingArticle_resetsMissesAndRemoval() {
        val u = run(listOf(stored("a", 200L, missed = 2), stored("b", 200L, missed = 3, removed = true)), setOf("a", "b"))
        assertEquals(2, u.size)
        assertTrue(u.all { it.missedSyncs == 0 && !it.removed })
    }

    @Test
    fun presentAndCleanArticle_producesNoUpdate() {
        assertTrue(run(listOf(stored("a", 200L)), setOf("a")).isEmpty())
    }

    @Test
    fun alreadyRemovedArticle_staysRemovedWithoutFurtherCounting() {
        assertTrue(run(listOf(stored("a", 200L, missed = 3, removed = true)), setOf("other")).isEmpty())
    }
}
