package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadPositionSyncPrefTest {

    @Test
    fun default_isOn() {
        assertEquals(ReadPositionSyncPref.ON, ReadPositionSyncPref.default)
        assertTrue(ReadPositionSyncPref.default.value)
    }

    @Test
    fun fromPreferences_readsExplicitBoolean() {
        assertEquals(
            ReadPositionSyncPref.ON,
            ReadPositionSyncPref.fromPreferences(mutablePreferencesOf(DataStoreKeys.ReadPositionSync.key to true)),
        )
        assertEquals(
            ReadPositionSyncPref.OFF,
            ReadPositionSyncPref.fromPreferences(mutablePreferencesOf(DataStoreKeys.ReadPositionSync.key to false)),
        )
    }

    @Test
    fun fromPreferences_fallsBackToDefaultWhenMissing() {
        assertEquals(ReadPositionSyncPref.default, ReadPositionSyncPref.fromPreferences(mutablePreferencesOf()))
    }

    @Test
    fun notOperator_invertsState() {
        assertFalse((!ReadPositionSyncPref.ON).value)
        assertTrue((!ReadPositionSyncPref.OFF).value)
    }

    @Test
    fun toSettings_mapsPreference() {
        val prefs = mutablePreferencesOf(DataStoreKeys.ReadPositionSync.key to false)
        val settings = prefs.toSettings()
        assertEquals(ReadPositionSyncPref.OFF, settings.readPositionSync)
        assertEquals(ReadPositionSyncPref.ON, mutablePreferencesOf().toSettings().readPositionSync)
    }
}
