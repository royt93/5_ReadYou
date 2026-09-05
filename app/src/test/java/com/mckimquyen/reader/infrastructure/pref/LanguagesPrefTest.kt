package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LanguagesPrefTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fromValue_returnsExpectedLanguageObject() {
        assertEquals(LanguagesPref.UseDeviceLanguages, LanguagesPref.fromValue(0))
        assertEquals(LanguagesPref.English, LanguagesPref.fromValue(1))
        assertEquals(LanguagesPref.Vietnamese, LanguagesPref.fromValue(17))
        assertEquals(LanguagesPref.ChineseSimplified, LanguagesPref.fromValue(2))
        assertEquals(LanguagesPref.Japanese, LanguagesPref.fromValue(14))
    }

    @Test
    fun toLanguageTag_matchesAndroidStandards() {
        assertEquals("", LanguagesPref.UseDeviceLanguages.toLanguageTag())
        assertEquals("en-US", LanguagesPref.English.toLanguageTag())
        assertEquals("vi", LanguagesPref.Vietnamese.toLanguageTag())
        assertEquals("zh-CN", LanguagesPref.ChineseSimplified.toLanguageTag())
        assertEquals("ja", LanguagesPref.Japanese.toLanguageTag())
    }

    @Test
    fun getLocale_returnsValidLocales() {
        assertEquals("en", LanguagesPref.English.getLocale().language)
        assertEquals("vi", LanguagesPref.Vietnamese.getLocale().language)
        assertEquals("zh", LanguagesPref.ChineseSimplified.getLocale().language)
        assertNotNull(LanguagesPref.UseDeviceLanguages.getLocale())
    }

    @Test
    fun applyLocale_persistsSynchronouslyToSharedPreferences() {
        LanguagesPref.Vietnamese.applyLocale(context)

        val prefs = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        assertEquals(17, prefs.getInt("languages", 0))
        assertEquals("vi", prefs.getString("language_tag", ""))

        LanguagesPref.English.applyLocale(context)
        assertEquals(1, prefs.getInt("languages", 0))
        assertEquals("en-US", prefs.getString("language_tag", ""))

        LanguagesPref.UseDeviceLanguages.applyLocale(context)
        assertEquals(0, prefs.getInt("languages", -1))
        assertEquals("", prefs.getString("language_tag", "not_empty"))
    }

    @Test
    fun put_persistsToDataStoreAndSharedPreferences() {
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        LanguagesPref.English.put(context, testScope)

        val prefs = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        assertEquals(1, prefs.getInt("languages", 0))
        assertEquals("en-US", prefs.getString("language_tag", ""))

        val dsValue = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE).getInt("languages", 0)
        assertEquals(1, dsValue)
    }
}
