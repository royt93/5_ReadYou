package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingBionicPrefTest {

    @Test
    fun `defaults to off so existing readers see no change`() {
        assertEquals(ReadingBionicPref.OFF, ReadingBionicPref.default)
        assertFalse(ReadingBionicPref.default.value)
    }

    @Test
    fun `reads a stored choice back`() {
        assertEquals(
            ReadingBionicPref.ON,
            ReadingBionicPref.fromPreferences(mutablePreferencesOf(DataStoreKeys.ReadingBionic.key to true)),
        )
        assertEquals(
            ReadingBionicPref.OFF,
            ReadingBionicPref.fromPreferences(mutablePreferencesOf(DataStoreKeys.ReadingBionic.key to false)),
        )
    }

    @Test
    fun `an unset preference falls back to the default instead of throwing`() {
        assertEquals(ReadingBionicPref.default, ReadingBionicPref.fromPreferences(mutablePreferencesOf()))
    }

    @Test
    fun `not flips the value both ways`() {
        assertTrue((!ReadingBionicPref.OFF).value)
        assertFalse((!ReadingBionicPref.ON).value)
    }

    @Test
    fun `settings snapshot carries the stored bionic choice`() {
        val settings = mutablePreferencesOf(DataStoreKeys.ReadingBionic.key to true).toSettings()
        assertEquals(ReadingBionicPref.ON, settings.readingBionic)
        assertEquals(ReadingBionicPref.OFF, mutablePreferencesOf().toSettings().readingBionic)
    }
}
