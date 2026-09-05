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
import com.mckimquyen.reader.ui.ext.putBlocking
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
    object Japanese : LanguagesPref(14)
    object Portuguese : LanguagesPref(15)
    object PortugueseBrazil : LanguagesPref(16)
    object Vietnamese : LanguagesPref(17)
    object Arabic : LanguagesPref(18)
    object Turkish : LanguagesPref(19)
    object Ukrainian : LanguagesPref(20)
    object Dutch : LanguagesPref(21)
    object Romanian : LanguagesPref(22)
    object Swedish : LanguagesPref(23)
    object NorwegianBokmal : LanguagesPref(24)
    object NorwegianNynorsk : LanguagesPref(25)
    object Danish : LanguagesPref(26)
    object Hungarian : LanguagesPref(27)
    object Bulgarian : LanguagesPref(28)
    object Catalan : LanguagesPref(29)
    object Slovenian : LanguagesPref(30)
    object Serbian : LanguagesPref(31)
    object Hebrew : LanguagesPref(32)
    object Persian : LanguagesPref(33)
    object Azerbaijani : LanguagesPref(34)
    object Kannada : LanguagesPref(35)
    object LiteraryChinese : LanguagesPref(36)
    object Malayalam : LanguagesPref(37)
    object Burmese : LanguagesPref(38)

    fun toLanguageTag(): String =
        when (this) {
            UseDeviceLanguages -> ""
            English -> "en-US"
            ChineseSimplified -> "zh-CN"
            German -> "de-DE"
            French -> "fr-FR"
            Czech -> "cs-CZ"
            Italian -> "it-IT"
            Hindi -> "hi-IN"
            Spanish -> "es-ES"
            Polish -> "pl-PL"
            Russian -> "ru-RU"
            Basque -> "eu-ES"
            Indonesian -> "in-ID"
            ChineseTraditional -> "zh-TW"
            Japanese -> "ja"
            Portuguese -> "pt"
            PortugueseBrazil -> "pt-BR"
            Vietnamese -> "vi"
            Arabic -> "ar"
            Turkish -> "tr"
            Ukrainian -> "uk"
            Dutch -> "nl"
            Romanian -> "ro"
            Swedish -> "sv"
            NorwegianBokmal -> "nb-NO"
            NorwegianNynorsk -> "nn"
            Danish -> "da"
            Hungarian -> "hu"
            Bulgarian -> "bg"
            Catalan -> "ca"
            Slovenian -> "sl"
            Serbian -> "sr"
            Hebrew -> "iw"
            Persian -> "fa"
            Azerbaijani -> "az"
            Kannada -> "kn"
            LiteraryChinese -> "lzh"
            Malayalam -> "ml"
            Burmese -> "my"
        }

    fun applyLocale(context: Context) {
        // Synchronously commit to SharedPreferences
        context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("languages", value)
            .putString("language_tag", toLanguageTag())
            .commit()

        val tag = toLanguageTag()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            if (tag.isEmpty()) {
                localeManager?.applicationLocales = android.os.LocaleList.getEmptyLocaleList()
            } else {
                localeManager?.applicationLocales = android.os.LocaleList.forLanguageTags(tag)
            }
        } else {
            if (tag.isEmpty()) {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                )
            } else {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                )
            }
        }
    }

    override fun put(context: Context, scope: CoroutineScope) {
        context.dataStore.putBlocking(
            DataStoreKeys.Languages,
            value
        )
        applyLocale(context)
    }

    @Suppress("UNUSED_PARAMETER")
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
            Japanese -> context.getString(R.string.japanese)
            Portuguese -> context.getString(R.string.portuguese)
            PortugueseBrazil -> context.getString(R.string.portuguese_brazil)
            Vietnamese -> context.getString(R.string.vietnamese)
            Arabic -> context.getString(R.string.arabic)
            Turkish -> context.getString(R.string.turkish)
            Ukrainian -> context.getString(R.string.ukrainian)
            Dutch -> context.getString(R.string.dutch)
            Romanian -> context.getString(R.string.romanian)
            Swedish -> context.getString(R.string.swedish)
            NorwegianBokmal -> context.getString(R.string.norwegian_bokmal)
            NorwegianNynorsk -> context.getString(R.string.norwegian_nynorsk)
            Danish -> context.getString(R.string.danish)
            Hungarian -> context.getString(R.string.hungarian)
            Bulgarian -> context.getString(R.string.bulgarian)
            Catalan -> context.getString(R.string.catalan)
            Slovenian -> context.getString(R.string.slovenian)
            Serbian -> context.getString(R.string.serbian)
            Hebrew -> context.getString(R.string.hebrew)
            Persian -> context.getString(R.string.persian)
            Azerbaijani -> context.getString(R.string.azerbaijani)
            Kannada -> context.getString(R.string.kannada)
            LiteraryChinese -> context.getString(R.string.literary_chinese)
            Malayalam -> context.getString(R.string.malayalam)
            Burmese -> context.getString(R.string.burmese)
        }

    fun getLocale(): Locale =
        when (this) {
            UseDeviceLanguages -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.content.res.Resources.getSystem().configuration.locales.get(0)
                } else {
                    @Suppress("DEPRECATION")
                    android.content.res.Resources.getSystem().configuration.locale
                }
            }
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
            Japanese -> Locale.Builder().setLanguage("ja").build()
            Portuguese -> Locale.Builder().setLanguage("pt").build()
            PortugueseBrazil -> Locale.Builder().setLanguage("pt").setRegion("BR").build()
            Vietnamese -> Locale.Builder().setLanguage("vi").build()
            Arabic -> Locale.Builder().setLanguage("ar").build()
            Turkish -> Locale.Builder().setLanguage("tr").build()
            Ukrainian -> Locale.Builder().setLanguage("uk").build()
            Dutch -> Locale.Builder().setLanguage("nl").build()
            Romanian -> Locale.Builder().setLanguage("ro").build()
            Swedish -> Locale.Builder().setLanguage("sv").build()
            NorwegianBokmal -> Locale.Builder().setLanguage("nb").setRegion("NO").build()
            NorwegianNynorsk -> Locale.Builder().setLanguage("nn").build()
            Danish -> Locale.Builder().setLanguage("da").build()
            Hungarian -> Locale.Builder().setLanguage("hu").build()
            Bulgarian -> Locale.Builder().setLanguage("bg").build()
            Catalan -> Locale.Builder().setLanguage("ca").build()
            Slovenian -> Locale.Builder().setLanguage("sl").build()
            Serbian -> Locale.Builder().setLanguage("sr").build()
            Hebrew -> Locale.Builder().setLanguage("iw").build()
            Persian -> Locale.Builder().setLanguage("fa").build()
            Azerbaijani -> Locale.Builder().setLanguage("az").build()
            Kannada -> Locale.Builder().setLanguage("kn").build()
            LiteraryChinese -> Locale.Builder().setLanguage("lzh").build()
            Malayalam -> Locale.Builder().setLanguage("ml").build()
            Burmese -> Locale.Builder().setLanguage("my").build()
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
            Japanese,
            Portuguese,
            PortugueseBrazil,
            Vietnamese,
            Arabic,
            Turkish,
            Ukrainian,
            Dutch,
            Romanian,
            Swedish,
            NorwegianBokmal,
            NorwegianNynorsk,
            Danish,
            Hungarian,
            Bulgarian,
            Catalan,
            Slovenian,
            Serbian,
            Hebrew,
            Persian,
            Azerbaijani,
            Kannada,
            LiteraryChinese,
            Malayalam,
            Burmese,
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
                14 -> Japanese
                15 -> Portuguese
                16 -> PortugueseBrazil
                17 -> Vietnamese
                18 -> Arabic
                19 -> Turkish
                20 -> Ukrainian
                21 -> Dutch
                22 -> Romanian
                23 -> Swedish
                24 -> NorwegianBokmal
                25 -> NorwegianNynorsk
                26 -> Danish
                27 -> Hungarian
                28 -> Bulgarian
                29 -> Catalan
                30 -> Slovenian
                31 -> Serbian
                32 -> Hebrew
                33 -> Persian
                34 -> Azerbaijani
                35 -> Kannada
                36 -> LiteraryChinese
                37 -> Malayalam
                38 -> Burmese
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
                14 -> Japanese
                15 -> Portuguese
                16 -> PortugueseBrazil
                17 -> Vietnamese
                18 -> Arabic
                19 -> Turkish
                20 -> Ukrainian
                21 -> Dutch
                22 -> Romanian
                23 -> Swedish
                24 -> NorwegianBokmal
                25 -> NorwegianNynorsk
                26 -> Danish
                27 -> Hungarian
                28 -> Bulgarian
                29 -> Catalan
                30 -> Slovenian
                31 -> Serbian
                32 -> Hebrew
                33 -> Persian
                34 -> Azerbaijani
                35 -> Kannada
                36 -> LiteraryChinese
                37 -> Malayalam
                38 -> Burmese
                else -> default
            }
    }
}
