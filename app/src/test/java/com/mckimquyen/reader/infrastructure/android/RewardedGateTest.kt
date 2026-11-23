package com.mckimquyen.reader.infrastructure.android

import android.app.Activity
import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardedGateTest {
    private val activity = mockk<Activity>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private class Spy(var shown: Int = 0, var preloaded: Int = 0, var answer: Boolean = true)

    private fun gate(vip: Boolean, spy: Spy) = RewardedGate(
        isVip = { vip },
        show = { _, done -> spy.shown++; done(spy.answer) },
        preload = { spy.preloaded++ },
    )

    @Test
    fun aVip_getsTheRewardAtOnce_andNoAdIsShown() {
        val spy = Spy(answer = false) // even if the SDK would refuse a VIP
        val got = mutableListOf<Boolean>()
        gate(vip = true, spy).request(activity) { got += it }
        assertEquals(listOf(true), got)
        assertEquals("no ad for a VIP", 0, spy.shown)
    }

    @Test
    fun aNonVip_goesThroughTheSdk_andGetsItsAnswer() {
        val earned = Spy(answer = true); val refused = Spy(answer = false)
        val a = mutableListOf<Boolean>(); val b = mutableListOf<Boolean>()
        gate(vip = false, earned).request(activity) { a += it }
        gate(vip = false, refused).request(activity) { b += it }
        assertEquals(listOf(true), a); assertEquals(listOf(false), b)
        assertEquals(1, earned.shown); assertEquals(1, refused.shown)
    }

    @Test
    fun theResultIsDeliveredOnce() {
        val spy = Spy()
        var calls = 0
        gate(vip = false, spy).request(activity) { calls++ }
        gate(vip = true, spy).request(activity) { calls++ }
        assertEquals(2, calls) // one per request, never two for one request
    }

    @Test
    fun requestAdOnly_neverSkipsTheAd_evenForAVip_soVipDaysAreNotGivenForFree() {
        val spy = Spy(answer = false)
        val got = mutableListOf<Boolean>()
        gate(vip = true, spy).requestAdOnly(activity) { got += it }
        assertEquals("the SDK answer is passed through", listOf(false), got)
        assertEquals(1, spy.shown)
    }

    @Test
    fun warmUp_preloadsForANonVip_andNotForAVip() {
        val a = Spy(); val b = Spy()
        gate(vip = false, a).warmUp(context)
        gate(vip = true, b).warmUp(context)
        assertEquals(1, a.preloaded); assertEquals(0, b.preloaded)
        assertTrue(a.shown == 0 && b.shown == 0)
    }
}
