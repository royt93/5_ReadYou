package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import android.os.LocaleList
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import java.util.*

sealed class LanguagesPref(val value: Int) : Pref() {
    object UseDeviceLanguages : LanguagesPref(0)
    object English : LanguagesPref(1)
    object ChineseSimplified : LanguagesPref(2)
    object German : LanguagesPref(3)
    object French : LanguagesPref(4)
    object Czech : LanguagesPref(5)
    object Italian : LanguagesPref(6)
    object Hindi : LanguagesPref(7)
    object Spanish : LanguagesPref(8)
    object Polish : LanguagesPref(9)
    object Russian : LanguagesPref(10)
    object Basque : LanguagesPref(11)
    object Indonesian : LanguagesPref(12)

    object ChineseTraditional : LanguagesPref(13)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.Languages,
                value
            )
            setLocale(context)
        }
    }

    fun toDesc(context: Context): String =
        when (this) {
            UseDeviceLanguages -> context.getString(R.string.use_device_languages)
            English -> context.getString(R.string.english)
            ChineseSimplified -> context.getString(R.string.chinese_simplified)
            German -> context.getString(R.string.german)
            French -> context.getString(R.string.french)
            Czech -> context.getString(R.string.czech)
            Italian -> context.getString(R.string.italian)
            Hindi -> context.getString(R.string.hindi)
            Spanish -> context.getString(R.string.spanish)
            Polish -> context.getString(R.string.polish)
            Russian -> context.getString(R.string.russian)
            Basque -> context.getString(R.string.basque)
            Indonesian -> context.getString(R.string.indonesian)
            ChineseTraditional -> context.getString(R.string.chinese_traditional)
        }

    fun getLocale(): Locale =
        when (this) {
            UseDeviceLanguages -> LocaleList.getDefault().get(0)
            English -> Locale.Builder().setLanguage("en").setRegion("US").build()
            ChineseSimplified -> Locale.Builder().setLanguage("zh").setRegion("CN").build()
            German -> Locale.Builder().setLanguage("de").setRegion("DE").build()
            French -> Locale.Builder().setLanguage("fr").setRegion("FR").build()
            Czech -> Locale.Builder().setLanguage("cs").setRegion("CZ").build()
            Italian -> Locale.Builder().setLanguage("it").setRegion("IT").build()
            Hindi -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
            Spanish -> Locale.Builder().setLanguage("es").setRegion("ES").build()
            Polish -> Locale.Builder().setLanguage("pl").setRegion("PL").build()
            Russian -> Locale.Builder().setLanguage("ru").setRegion("RU").build()
            Basque -> Locale.Builder().setLanguage("eu").setRegion("ES").build()
            Indonesian -> Locale.Builder().setLanguage("in").setRegion("ID").build()
            ChineseTraditional -> Locale.Builder().setLanguage("zh").setRegion("TW").build()
        }

    fun setLocale(context: Context) {
        val locale = getLocale()
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        context.createConfigurationContext(configuration)

        val appConfiguration = context.applicationContext.resources.configuration
        appConfiguration.setLocale(locale)
        appConfiguration.setLocales(LocaleList(locale))
        context.applicationContext.createConfigurationContext(appConfiguration)
    }

    companion object {

        val default = UseDeviceLanguages
        val values = listOf(
            UseDeviceLanguages,
            English,
            ChineseSimplified,
            German,
            French,
            Czech,
            Italian,
            Hindi,
            Spanish,
            Polish,
            Russian,
            Basque,
            Indonesian,
            ChineseTraditional,
        )

        fun fromPreferences(preferences: Preferences): LanguagesPref =
            when (preferences[DataStoreKeys.Languages.key]) {
                0 -> UseDeviceLanguages
                1 -> English
                2 -> ChineseSimplified
                3 -> German
                4 -> French
                5 -> Czech
                6 -> Italian
                7 -> Hindi
                8 -> Spanish
                9 -> Polish
                10 -> Russian
                11 -> Basque
                12 -> Indonesian
                13 -> ChineseTraditional
                else -> default
            }

        fun fromValue(value: Int): LanguagesPref =
            when (value) {
                0 -> UseDeviceLanguages
                1 -> English
                2 -> ChineseSimplified
                3 -> German
                4 -> French
                5 -> Czech
                6 -> Italian
                7 -> Hindi
                8 -> Spanish
                9 -> Polish
                10 -> Russian
                11 -> Basque
                12 -> Indonesian
                13 -> ChineseTraditional
                else -> default
            }
    }
}
