package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mckimquyen.reader.domain.model.bilingual.BilingualLanguages
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingBilingualPrefTest {

    @Test
    fun `defaults to off and follow-app-language`() {
        assertEquals(ReadingBilingualPref.OFF, ReadingBilingualPref.default)
        assertEquals(BilingualLanguages.AUTO, ReadingBilingualTargetPref.default.value)
    }

    @Test
    fun `reads a stored switch back and falls back when unset`() {
        assertEquals(
            ReadingBilingualPref.ON,
            ReadingBilingualPref.fromPreferences(mutablePreferencesOf(DataStoreKeys.ReadingBilingual.key to true)),
        )
        assertEquals(ReadingBilingualPref.default, ReadingBilingualPref.fromPreferences(mutablePreferencesOf()))
    }

    @Test
    fun `not flips the switch both ways`() {
        assertTrue((!ReadingBilingualPref.OFF).value)
        assertFalse((!ReadingBilingualPref.ON).value)
    }

    @Test
    fun `reads a stored target language back`() {
        val prefs = mutablePreferencesOf(DataStoreKeys.ReadingBilingualTarget.key to "de")
        assertEquals("de", ReadingBilingualTargetPref.fromPreferences(prefs).value)
    }

    @Test
    fun `a stored target that is no longer supported reads back as auto`() {
        val prefs = mutablePreferencesOf(DataStoreKeys.ReadingBilingualTarget.key to "xx")
        assertEquals(BilingualLanguages.AUTO, ReadingBilingualTargetPref.fromPreferences(prefs).value)
    }

    @Test
    fun `settings snapshot carries both bilingual choices`() {
        val settings = mutablePreferencesOf(
            DataStoreKeys.ReadingBilingual.key to true,
            DataStoreKeys.ReadingBilingualTarget.key to "ja",
        ).toSettings()
        assertEquals(ReadingBilingualPref.ON, settings.readingBilingual)
        assertEquals("ja", settings.readingBilingualTarget.value)

        val empty = mutablePreferencesOf().toSettings()
        assertEquals(ReadingBilingualPref.OFF, empty.readingBilingual)
        assertEquals(BilingualLanguages.AUTO, empty.readingBilingualTarget.value)
    }
}
