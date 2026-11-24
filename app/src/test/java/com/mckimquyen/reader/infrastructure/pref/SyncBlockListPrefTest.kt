package com.mckimquyen.reader.infrastructure.pref

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `joinToString { "$it\n" }` passes the lambda as the transform, so ", " was inserted between lines: ["ads.com","spam.net"] was stored as "ads.com\n, spam.net\n"
 * and read back as ["ads.com", ", spam.net", ""], a rule that never matches. The Room TypeConverter saved that on every account write.
 */
class SyncBlockListPrefTest {
    @Test
    fun roundTripKeepsEveryRule() {
        val rules = listOf("ads.com", "spam.net", "tracker.io")
        assertEquals(rules, SyncBlockListPref.of(SyncBlockListPref.toString(rules)))
    }

    @Test
    fun theStoredStringHasNoSeparatorOtherThanTheNewline() {
        // The reader repairs the old ", " damage, so a round trip alone cannot tell the bug from the fix: the stored text itself must be clean.
        assertEquals("ads.com\nspam.net", SyncBlockListPref.toString(listOf("ads.com", "spam.net")))
    }

    @Test
    fun repeatedRoundTripsDoNotGrow() {
        var list = listOf("a", "b")
        repeat(5) { list = SyncBlockListPref.of(SyncBlockListPref.toString(list)) }
        assertEquals(listOf("a", "b"), list)
    }

    @Test
    fun blankLinesAndPaddingAreDropped() {
        assertEquals(listOf("a", "b"), SyncBlockListPref.of("  a \n\n b\n"))
    }

    @Test
    fun emptyListStaysEmpty() {
        assertEquals(emptyList<String>(), SyncBlockListPref.of(SyncBlockListPref.toString(emptyList())))
    }

    @Test
    fun valuesDamagedByTheOldBugAreRepairedOnRead() {
        // What the old code stored for ["ads.com","spam.net"], and for a second write on top of it.
        assertEquals(listOf("ads.com", "spam.net"), SyncBlockListPref.of("ads.com\n, spam.net\n"))
        assertEquals(listOf("ads.com", "spam.net"), SyncBlockListPref.of("ads.com\n, , spam.net\n"))
    }

    @Test
    fun aRuleThatReallyStartsWithACommaIsKeptWhenNothingElseIsBroken() {
        // Only the old separator pattern ("\n, ") is repaired; a lone first line is left as typed.
        assertEquals(listOf(",weird.com"), SyncBlockListPref.of(",weird.com"))
    }
}
