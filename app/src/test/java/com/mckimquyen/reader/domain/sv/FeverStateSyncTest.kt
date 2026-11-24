package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A Fever answer without `unread_item_ids` or `saved_item_ids` used to mark every article unread and remove every star on the phone (null read as "none").
 */
class FeverStateSyncTest {
    @Test
    fun aMissingListLeavesTheFlagAlone() {
        assertNull(FeverStateSync.targetFlag("1", FeverStateSync.parseIds(null), local = true))
        assertNull(FeverStateSync.targetFlag("1", FeverStateSync.parseIds(null), local = false))
    }

    @Test
    fun anEmptyAnswerIsARealEmptyAnswer() {
        // "" means the server really has nothing unread/starred: a locally starred article must be cleared.
        val ids = FeverStateSync.parseIds("")
        assertEquals(emptySet<String>(), ids)
        assertEquals(false, FeverStateSync.targetFlag("1", ids, local = true))
    }

    @Test
    fun anArticleInTheListBecomesTrueAndOneOutsideBecomesFalse() {
        val ids = FeverStateSync.parseIds("10, 20,30")
        assertEquals(true, FeverStateSync.targetFlag("20", ids, local = false))
        assertEquals(false, FeverStateSync.targetFlag("99", ids, local = true))
    }

    @Test
    fun noChangeWhenTheFlagAlreadyMatches() {
        val ids = FeverStateSync.parseIds("10,20")
        assertNull(FeverStateSync.targetFlag("10", ids, local = true))
        assertNull(FeverStateSync.targetFlag("99", ids, local = false))
    }

    @Test
    fun aSyncWithAnAbsentStarListDoesNotClearAnyStar() {
        val starred = FeverStateSync.parseIds(null)
        val localStars = listOf("1" to true, "2" to true, "3" to false)
        val changes = localStars.mapNotNull { (id, s) -> FeverStateSync.targetFlag(id, starred, s)?.let { id to it } }
        assertEquals(emptyList<Pair<String, Boolean>>(), changes)
    }
}
