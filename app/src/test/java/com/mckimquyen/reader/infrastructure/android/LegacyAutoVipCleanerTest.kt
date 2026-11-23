package com.mckimquyen.reader.infrastructure.android

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Old app builds (the 2026-04-01 release) wrote a fixed list of 21 test-phone ids into the ad SDK's preferences, and the SDK treats every phone in it as
 * "VIP, show no ads". The list is read again at every start, so those phones never saw an ad. The cleaner removes that list once and leaves everything
 * else alone: VIP from a token, a redeem code or the trial lives under other keys and must survive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LegacyAutoVipCleanerTest {

    private lateinit var sdkPrefs: SharedPreferences
    private lateinit var appPrefs: SharedPreferences
    private val removed = mutableListOf<List<String>>()

    private fun cleaner(list: List<String>) = LegacyAutoVipCleaner(
        readList = { list },
        removeFromSdk = { removed += it },
        flag = FlagStore(appPrefs),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sdkPrefs = context.getSharedPreferences("sdk_fake", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        appPrefs = context.getSharedPreferences("cleaner_flag_fake", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        removed.clear()
    }

    @Test
    fun removesTheWholeListOnce() {
        val ids = (1..21).map { "id-$it" }
        assertTrue(cleaner(ids).runOnce())
        assertEquals(listOf(ids), removed)
    }

    @Test
    fun doesNotRunTwice() {
        val c = cleaner(listOf("a", "b"))
        assertTrue(c.runOnce())
        assertFalse("the second start must not touch the SDK again", c.runOnce())
        assertEquals(1, removed.size)
    }

    @Test
    fun anEmptyList_isMarkedDoneWithoutCallingTheSdk() {
        val c = cleaner(emptyList())
        assertFalse(c.runOnce())
        assertTrue(removed.isEmpty())
        assertFalse("nothing to do is also remembered", cleaner(listOf("late")).runOnce())
    }

    @Test
    fun aFailureLeavesTheFlagUnset_soItIsTriedAgainNextStart() {
        val boom = LegacyAutoVipCleaner(readList = { listOf("a") }, removeFromSdk = { error("sdk not ready") }, flag = FlagStore(appPrefs))
        assertFalse(boom.runOnce())
        assertTrue(cleaner(listOf("a")).runOnce())
    }
}
