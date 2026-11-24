package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenGuardTest {

    private class Switch {
        val calls = mutableListOf<Boolean>()
        fun set(v: Boolean) { calls += v }
    }

    @Test
    fun suppressesBeforeTheLaunch_andClearsAfterTheResult() {
        val s = Switch()
        val guard = AppOpenGuard(s::set)
        var launched = false
        val wrapped = guard.wrap<String> { launched = true; s.calls.add(true) }
        wrapped("x")
        assertTrue(launched)
        assertEquals(listOf(true, true), s.calls) // our suppress + the probe above
        guard.onResult()
        assertFalse("the last call must clear the suppression", s.calls.last())
    }

    @Test
    fun aThrowingLauncher_clearsTheSuppression() {
        val s = Switch()
        val guard = AppOpenGuard(s::set)
        try {
            guard.wrap<String> { error("no activity to handle this") }("x")
        } catch (_: IllegalStateException) { }
        assertFalse(s.calls.last())
    }

    @Test
    fun leavingForAnotherApp_suppressesUntilTheAppIsBackInFront() {
        val s = Switch()
        val guard = AppOpenGuard(s::set)
        var opened = 0
        guard.leaveForOtherApp { opened++ }
        assertEquals("the other app was started", 1, opened)
        assertEquals("the ad is off while the user is in the other app", listOf(true), s.calls)
        guard.onResult() // MainActivity.onResume: the app is in front again
        assertEquals(listOf(true, false), s.calls)
    }

    @Test
    fun leavingForAnotherApp_thatCannotBeStarted_releasesAtOnceAndRethrows() {
        val s = Switch()
        val guard = AppOpenGuard(s::set)
        var thrown = false
        try {
            guard.leaveForOtherApp { error("no activity found") }
        } catch (_: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(listOf(true, false), s.calls)
    }

    @Test
    fun aSecondResultWithoutALaunch_doesNotSuppressAgain() {
        val s = Switch()
        val guard = AppOpenGuard(s::set)
        guard.onResult()
        assertEquals(listOf(false), s.calls)
    }
}
