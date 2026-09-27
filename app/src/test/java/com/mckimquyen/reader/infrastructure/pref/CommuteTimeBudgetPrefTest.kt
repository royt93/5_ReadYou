package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class CommuteTimeBudgetPrefTest {

    @Test
    fun fromMinutes_validMinutes_returnsMatchingPref() {
        assertEquals(CommuteTimeBudgetPref.MIN_3, CommuteTimeBudgetPref.fromMinutes(3))
        assertEquals(CommuteTimeBudgetPref.MIN_4, CommuteTimeBudgetPref.fromMinutes(4))
        assertEquals(CommuteTimeBudgetPref.MIN_8, CommuteTimeBudgetPref.fromMinutes(8))
        assertEquals(CommuteTimeBudgetPref.MIN_15, CommuteTimeBudgetPref.fromMinutes(15))
    }

    @Test
    fun fromMinutes_unknownMinutes_returnsDefault4Minutes() {
        assertEquals(CommuteTimeBudgetPref.default, CommuteTimeBudgetPref.fromMinutes(0))
        assertEquals(CommuteTimeBudgetPref.default, CommuteTimeBudgetPref.fromMinutes(10))
        assertEquals(4, CommuteTimeBudgetPref.default.minutes)
    }

    @Test
    fun fromPreferences_readsCorrectValue() {
        val prefs = mutablePreferencesOf(DataStoreKeys.CommuteTimeBudget.key to 8)
        val result = CommuteTimeBudgetPref.fromPreferences(prefs)
        assertEquals(CommuteTimeBudgetPref.MIN_8, result)
    }

    @Test
    fun fromPreferences_missingKey_returnsDefault() {
        val emptyPrefs = mutablePreferencesOf()
        val result = CommuteTimeBudgetPref.fromPreferences(emptyPrefs)
        assertEquals(CommuteTimeBudgetPref.default, result)
    }
}
