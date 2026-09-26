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
import org.junit.Assert.assertNull
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

    // ---- ZEN-05: edition time validation, setters, and next-occurrence scheduling ----

    @Test
    fun isValidTime_acceptsHhMmAndRejectsMalformed() {
        assertTrue(ZenDailyEditionManager.isValidTime("00:00"))
        assertTrue(ZenDailyEditionManager.isValidTime("07:00"))
        assertTrue(ZenDailyEditionManager.isValidTime("23:59"))
        assertTrue(ZenDailyEditionManager.isValidTime(" 06:30 "))

        assertFalse(ZenDailyEditionManager.isValidTime("24:00"))
        assertFalse(ZenDailyEditionManager.isValidTime("07:60"))
        assertFalse(ZenDailyEditionManager.isValidTime("7:00"))
        assertFalse(ZenDailyEditionManager.isValidTime("07"))
        assertFalse(ZenDailyEditionManager.isValidTime(""))
        assertFalse(ZenDailyEditionManager.isValidTime("abc"))
    }

    @Test
    fun millisUntilNextOccurrence_picksNearestUpcomingSlotToday() {
        // 06:00 today -> next is 07:00 today (1 hour away).
        val now = calendarAt(hour = 6, minute = 0)
        val delay = ZenDailyEditionManager.millisUntilNextOccurrence(listOf("07:00", "20:00"), now)

        assertEquals(60L * 60L * 1000L, delay)
        assertEquals(7 to 0, hourMinuteOf(now + delay!!))
    }

    @Test
    fun millisUntilNextOccurrence_morningPassed_usesEveningSlot() {
        // 12:00 today -> morning 07:00 already passed, next is 20:00 today (8 hours away).
        val now = calendarAt(hour = 12, minute = 0)
        val delay = ZenDailyEditionManager.millisUntilNextOccurrence(listOf("07:00", "20:00"), now)

        assertEquals(8L * 60L * 60L * 1000L, delay)
        assertEquals(20 to 0, hourMinuteOf(now + delay!!))
    }

    @Test
    fun millisUntilNextOccurrence_bothSlotsPassed_rollsOverToTomorrow() {
        // 22:00 today -> both slots passed, next is 07:00 tomorrow (9 hours away).
        val now = calendarAt(hour = 22, minute = 0)
        val delay = ZenDailyEditionManager.millisUntilNextOccurrence(listOf("07:00", "20:00"), now)

        assertEquals(9L * 60L * 60L * 1000L, delay)
        val (hour, minute) = hourMinuteOf(now + delay!!)
        assertEquals(7 to 0, hour to minute)
        // Must land on the following calendar day, not the same day.
        assertTrue((now + delay) > now)
    }

    @Test
    fun millisUntilNextOccurrence_exactlyOnSlot_doesNotFireImmediately() {
        // Exactly 07:00 -> the 07:00 slot must NOT be returned as 0 delay (that would re-fire
        // instantly in a loop); it rolls to tomorrow, so the nearest slot is 20:00 today.
        val now = calendarAt(hour = 7, minute = 0)
        val delay = ZenDailyEditionManager.millisUntilNextOccurrence(listOf("07:00", "20:00"), now)

        assertEquals(13L * 60L * 60L * 1000L, delay)
        assertEquals(20 to 0, hourMinuteOf(now + delay!!))
    }

    @Test
    fun millisUntilNextOccurrence_exactlyOnSlot_whenOnlySlotRollsFullDay() {
        // With a single 07:00 slot and now exactly 07:00, the next occurrence is 24h later.
        val now = calendarAt(hour = 7, minute = 0)
        val delay = ZenDailyEditionManager.millisUntilNextOccurrence(listOf("07:00"), now)

        assertEquals(24L * 60L * 60L * 1000L, delay)
        assertEquals(7 to 0, hourMinuteOf(now + delay!!))
    }

    @Test
    fun millisUntilNextOccurrence_ignoresInvalidSlotsAndUsesValidOne() {
        val now = calendarAt(hour = 6, minute = 0)
        val delay = ZenDailyEditionManager.millisUntilNextOccurrence(listOf("bad", "07:00"), now)

        assertEquals(60L * 60L * 1000L, delay)
    }

    @Test
    fun millisUntilNextOccurrence_allSlotsInvalid_returnsNull() {
        val now = calendarAt(hour = 6, minute = 0)
        assertNull(ZenDailyEditionManager.millisUntilNextOccurrence(listOf("", "nope"), now))
        assertNull(ZenDailyEditionManager.millisUntilNextOccurrence(emptyList(), now))
    }

    @Test
    fun setMorningTime_persistsAndUpdatesFlow_withoutSchedulingWhenDisabled() {
        val manager = ZenDailyEditionManager(context, eagerScope, Dispatchers.Unconfined)

        assertTrue(manager.setMorningTime("06:30"))

        verify { editor.putString("key_morning_time", "06:30") }
        assertEquals("06:30", manager.morningTime.value)
        // Disabled feature must not touch WorkManager (getInstance would blow up on the mock context).
    }

    @Test
    fun setEveningTime_persistsAndUpdatesFlow() {
        val manager = ZenDailyEditionManager(context, eagerScope, Dispatchers.Unconfined)

        assertTrue(manager.setEveningTime("21:00"))

        verify { editor.putString("key_evening_time", "21:00") }
        assertEquals("21:00", manager.eveningTime.value)
    }

    @Test
    fun setMorningTime_invalidFormat_isRejectedAndLeavesStateUntouched() {
        val manager = ZenDailyEditionManager(context, eagerScope, Dispatchers.Unconfined)
        val before = manager.morningTime.value

        assertFalse(manager.setMorningTime("25:99"))

        verify(exactly = 0) { editor.putString("key_morning_time", any()) }
        assertEquals(before, manager.morningTime.value)
    }

    @Test
    fun setMorningTime_beforeAsyncLoad_isNotOverwrittenByLateLoad() {
        every { prefs.getString("key_morning_time", "07:00") } returns "07:00"
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val manager = ZenDailyEditionManager(context, scope, dispatcher)

        manager.setMorningTime("06:30")
        scope.testScheduler.advanceUntilIdle()

        assertEquals("06:30", manager.morningTime.value)
    }

    private fun calendarAt(hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun hourMinuteOf(millis: Long): Pair<Int, Int> =
        java.util.Calendar.getInstance().apply { timeInMillis = millis }.let {
            it.get(java.util.Calendar.HOUR_OF_DAY) to it.get(java.util.Calendar.MINUTE)
        }
}
