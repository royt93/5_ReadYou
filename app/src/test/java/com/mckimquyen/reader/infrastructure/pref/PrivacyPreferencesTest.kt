package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPreferencesTest {

    @Test
    fun dohProviderPref_defaultsToSystemAndParsesCorrectly() {
        val emptyPrefs = mutablePreferencesOf()
        assertEquals(DohProviderPref.SYSTEM, DohProviderPref.fromPreferences(emptyPrefs))

        val cfPrefs = mutablePreferencesOf(intPreferencesKey("doh_provider") to 1)
        assertEquals(DohProviderPref.CLOUDFLARE, DohProviderPref.fromPreferences(cfPrefs))

        val agPrefs = mutablePreferencesOf(intPreferencesKey("doh_provider") to 2)
        assertEquals(DohProviderPref.ADGUARD, DohProviderPref.fromPreferences(agPrefs))
    }

    @Test
    fun urlTrackingCleanPref_defaultsToTrue() {
        val emptyPrefs = mutablePreferencesOf()
        assertTrue(UrlTrackingCleanPref.fromPreferences(emptyPrefs).value)

        val offPrefs = mutablePreferencesOf(booleanPreferencesKey("url_tracking_clean") to false)
        assertFalse(UrlTrackingCleanPref.fromPreferences(offPrefs).value)
    }

    @Test
    fun htmlAntiTrackerPref_defaultsToTrue() {
        val emptyPrefs = mutablePreferencesOf()
        assertTrue(HtmlAntiTrackerPref.fromPreferences(emptyPrefs).value)

        val offPrefs = mutablePreferencesOf(booleanPreferencesKey("html_anti_tracker") to false)
        assertFalse(HtmlAntiTrackerPref.fromPreferences(offPrefs).value)
    }

    @Test
    fun headlineSignalsPref_defaultsToTrueAndParsesExplicitOff() {
        assertTrue(HeadlineSignalsPref.fromPreferences(mutablePreferencesOf()).value)

        val offPrefs = mutablePreferencesOf(booleanPreferencesKey("headline_signals_enabled") to false)
        assertFalse(HeadlineSignalsPref.fromPreferences(offPrefs).value)
    }

    @Test
    fun retractionRadarPref_defaultsToTrue() {
        assertTrue(RetractionRadarPref.fromPreferences(mutablePreferencesOf()).value)

        val offPrefs = mutablePreferencesOf(booleanPreferencesKey("retraction_radar") to false)
        assertFalse(RetractionRadarPref.fromPreferences(offPrefs).value)
    }
}
