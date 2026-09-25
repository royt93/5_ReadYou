package com.mckimquyen.reader.domain.zen

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalCoroutinesApi::class)
class ZenDailyEditionManagerTest {

    // Unconfined: the async initial load runs inline, so tests see persisted state right away.
    private val eagerScope = CoroutineScope(Dispatchers.Unconfined)

    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    @Before
    fun setUp() {
        every { context.getSharedPreferences("zen_daily_edition_prefs", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
    }

    @Test
    fun shouldSilenceImmediateNotification_whenEnabledAndBatchSilenceTrue() {
        every { prefs.getBoolean("key_daily_edition_enabled", false) } returns true
        every { prefs.getBoolean("key_daily_edition_batch_silence", true) } returns true

        val manager = ZenDailyEditionManager(context, eagerScope, Dispatchers.Unconfined)
        assertTrue(manager.shouldSilenceImmediateNotification())
    }

    @Test
    fun shouldSilenceImmediateNotification_returnsFalseWhenDisabled() {
        every { prefs.getBoolean("key_daily_edition_enabled", false) } returns false
        every { prefs.getBoolean("key_daily_edition_batch_silence", true) } returns true

        val manager = ZenDailyEditionManager(context, eagerScope, Dispatchers.Unconfined)
        assertFalse(manager.shouldSilenceImmediateNotification())
    }

    @Test
    fun setBatchSilence_updatesPreferences() {
        val manager = ZenDailyEditionManager(context, eagerScope, Dispatchers.Unconfined)
        manager.setBatchSilence(false)

        verify { editor.putBoolean("key_daily_edition_batch_silence", false) }
        assertFalse(manager.isBatchSilence.value)
    }

    @Test
    fun constructor_doesNotReadPreferences_untilAsyncLoadRuns() {
        every { prefs.getBoolean("key_daily_edition_enabled", false) } returns true
        every { prefs.getBoolean("key_daily_edition_batch_silence", true) } returns false
        every { prefs.getString("key_morning_time", "07:00") } returns "06:30"
        every { prefs.getString("key_evening_time", "20:00") } returns "21:15"
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)

        val manager = ZenDailyEditionManager(context, scope, dispatcher)

        // Constructor must not touch disk: no SharedPreferences lookup, flows hold defaults.
        verify(exactly = 0) { context.getSharedPreferences(any(), any()) }
        assertFalse(manager.isEnabled.value)
        assertTrue(manager.isBatchSilence.value)
        assertEquals("07:00", manager.morningTime.value)
        assertEquals("20:00", manager.eveningTime.value)

        scope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isEnabled.value)
        assertFalse(manager.isBatchSilence.value)
        assertEquals("06:30", manager.morningTime.value)
        assertEquals("21:15", manager.eveningTime.value)
    }

    @Test
    fun setBatchSilence_beforeAsyncLoad_isNotOverwrittenByLateLoad() {
        every { prefs.getBoolean("key_daily_edition_enabled", false) } returns true
        every { prefs.getBoolean("key_daily_edition_batch_silence", true) } returns true
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val manager = ZenDailyEditionManager(context, scope, dispatcher)

        manager.setBatchSilence(false)
        scope.testScheduler.advanceUntilIdle()

        assertFalse(manager.isBatchSilence.value)
        assertTrue(manager.isEnabled.value)
    }

    @Test
    fun shouldSilenceImmediateNotification_beforeAsyncLoad_usesPersistedSettings() {
        every { prefs.getBoolean("key_daily_edition_enabled", false) } returns true
        every { prefs.getBoolean("key_daily_edition_batch_silence", true) } returns true
        val dispatcher = StandardTestDispatcher()
        val manager = ZenDailyEditionManager(context, TestScope(dispatcher), dispatcher)

        // Async load not run yet: defaults (enabled=false) would wrongly say "do not silence".
        assertTrue(manager.shouldSilenceImmediateNotification())
    }
}
