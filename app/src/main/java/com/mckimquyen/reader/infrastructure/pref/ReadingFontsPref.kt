package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.ExternalFonts
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class ReadingFontsPref(val value: Int) : Pref() {
    object System : ReadingFontsPref(0)
    object Serif : ReadingFontsPref(1)
    object SansSerif : ReadingFontsPref(2)
    object Monospace : ReadingFontsPref(3)
    object Cursive : ReadingFontsPref(4)
    object External : ReadingFontsPref(5)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.ReadingFonts, value)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            System -> context.getString(R.string.system_default)
            Serif -> "Serif"
            SansSerif -> "Sans-Serif"
            Monospace -> "Monospace"
            Cursive -> "Cursive"
            External -> context.getString(R.string.external_fonts)
        }

    fun asFontFamily(context: Context): FontFamily =
        when (this) {
            System -> FontFamily.Default
            Serif -> FontFamily.Serif
            SansSerif -> FontFamily.SansSerif
            Monospace -> FontFamily.Monospace
            Cursive -> FontFamily.Cursive
            External -> ExternalFonts.loadReadingTypography(context).displayLarge.fontFamily ?: FontFamily.Default
        }

    companion object {

        val default = System
        val values = listOf(System, Serif, SansSerif, Monospace, Cursive, External)

        fun fromPreferences(preferences: Preferences): ReadingFontsPref =
            when (preferences[DataStoreKeys.ReadingFonts.key]) {
                0 -> System
                1 -> Serif
                2 -> SansSerif
                3 -> Monospace
                4 -> Cursive
                5 -> External
                else -> default
            }
    }
}
