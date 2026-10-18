package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EInkModePrefTest {

    @Test
    fun default_isOff() {
        assertEquals(EInkModePref.OFF, EInkModePref.default)
        assertFalse(EInkModePref.default.value)
    }

    @Test
    fun fromPreferences_readsExplicitBoolean() {
        assertEquals(
            EInkModePref.ON,
            EInkModePref.fromPreferences(mutablePreferencesOf(DataStoreKeys.EInkMode.key to true)),
        )
        assertEquals(
            EInkModePref.OFF,
            EInkModePref.fromPreferences(mutablePreferencesOf(DataStoreKeys.EInkMode.key to false)),
        )
    }

    @Test
    fun fromPreferences_fallsBackToOffWhenMissing() {
        assertEquals(EInkModePref.OFF, EInkModePref.fromPreferences(mutablePreferencesOf()))
    }

    @Test
    fun notOperator_invertsState() {
        assertTrue((!EInkModePref.OFF).value)
        assertFalse((!EInkModePref.ON).value)
    }

    @Test
    fun toSettings_mapsPreference() {
        val prefs = mutablePreferencesOf(DataStoreKeys.EInkMode.key to true)
        assertEquals(EInkModePref.ON, prefs.toSettings().eInkMode)
        assertEquals(EInkModePref.OFF, mutablePreferencesOf().toSettings().eInkMode)
    }
}
