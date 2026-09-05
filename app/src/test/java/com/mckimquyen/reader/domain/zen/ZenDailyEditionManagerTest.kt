package com.mckimquyen.reader.domain.zen

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ZenDailyEditionManagerTest {

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

        val manager = ZenDailyEditionManager(context)
        assertTrue(manager.shouldSilenceImmediateNotification())
    }

    @Test
    fun shouldSilenceImmediateNotification_returnsFalseWhenDisabled() {
        every { prefs.getBoolean("key_daily_edition_enabled", false) } returns false
        every { prefs.getBoolean("key_daily_edition_batch_silence", true) } returns true

        val manager = ZenDailyEditionManager(context)
        assertFalse(manager.shouldSilenceImmediateNotification())
    }

    @Test
    fun setBatchSilence_updatesPreferences() {
        val manager = ZenDailyEditionManager(context)
        manager.setBatchSilence(false)

        verify { editor.putBoolean("key_daily_edition_batch_silence", false) }
        assertFalse(manager.isBatchSilence.value)
    }
}
