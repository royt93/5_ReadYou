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
    fun aSecondResultWithoutALaunch_doesNotSuppressAgain() {
        val s = Switch()
        val guard = AppOpenGuard(s::set)
        guard.onResult()
        assertEquals(listOf(false), s.calls)
    }
}
