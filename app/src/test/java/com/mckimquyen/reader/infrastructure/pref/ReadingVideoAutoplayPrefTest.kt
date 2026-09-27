package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingVideoAutoplayPrefTest {

    @Test
    fun `defaults to on so videos start without a tap`() {
        assertEquals(ReadingVideoAutoplayPref.ON, ReadingVideoAutoplayPref.default)
        assertTrue(ReadingVideoAutoplayPref.default.value)
    }

    @Test
    fun `reads a stored choice back`() {
        val off = mutablePreferencesOf(DataStoreKeys.ReadingVideoAutoplay.key to false)
        assertEquals(ReadingVideoAutoplayPref.OFF, ReadingVideoAutoplayPref.fromPreferences(off))

        val on = mutablePreferencesOf(DataStoreKeys.ReadingVideoAutoplay.key to true)
        assertEquals(ReadingVideoAutoplayPref.ON, ReadingVideoAutoplayPref.fromPreferences(on))
    }

    @Test
    fun `an unset preference falls back to the default instead of throwing`() {
        assertEquals(
            ReadingVideoAutoplayPref.default,
            ReadingVideoAutoplayPref.fromPreferences(mutablePreferencesOf()),
        )
    }

    @Test
    fun `not flips the value both ways`() {
        assertFalse((!ReadingVideoAutoplayPref.ON).value)
        assertTrue((!ReadingVideoAutoplayPref.OFF).value)
    }

}
