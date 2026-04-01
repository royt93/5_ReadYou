package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

@Immutable
sealed class ReadingThemePref(val value: Int) : Pref() {

    object MaterialYou : ReadingThemePref(0)
    object Reeder : ReadingThemePref(1)
    object Paper : ReadingThemePref(2)
    object Custom : ReadingThemePref(3)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.ReadingTheme, value)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            MaterialYou -> "Material You"
            Reeder -> "Reeder"
            Paper -> "Paper"
            Custom -> "Custom"
        }

    fun applyTheme(context: Context, scope: CoroutineScope) {
        when (this) {
            MaterialYou -> {
                ReadingTitleBoldPref.default.put(context, scope)
                ReadingTitleUpperCasePref.default.put(context, scope)
                ReadingTitleAlignPref.default.put(context, scope)
                ReadingSubheadBoldPref.default.put(context, scope)
                ReadingSubheadUpperCasePref.default.put(context, scope)
                ReadingSubheadAlignPref.default.put(context, scope)
                ReadingTextBoldPref.default.put(context, scope)
                ReadingTextHorizontalPaddingPref.put(context, scope,
                    ReadingTextHorizontalPaddingPref.default)
                ReadingTextAlignPref.default.put(context, scope)
                ReadingLetterSpacingPref.put(context, scope, ReadingLetterSpacingPref.default)
                ReadingTextFontSizePref.put(context, scope, ReadingTextFontSizePref.default)
                ReadingImageRoundedCornersPref.put(context, scope, ReadingImageRoundedCornersPref.default)
                ReadingImageHorizontalPaddingPref.put(context, scope,
                    ReadingImageHorizontalPaddingPref.default)
                ReadingImageMaximizePref.default.put(context, scope)
            }

            Reeder -> {
                ReadingTitleBoldPref.ON.put(context, scope)
                ReadingTitleUpperCasePref.default.put(context, scope)
                ReadingTitleAlignPref.default.put(context, scope)
                ReadingSubheadBoldPref.ON.put(context, scope)
                ReadingSubheadUpperCasePref.default.put(context, scope)
                ReadingSubheadAlignPref.default.put(context, scope)
                ReadingTextBoldPref.default.put(context, scope)
                ReadingTextHorizontalPaddingPref.put(context, scope,
                    ReadingTextHorizontalPaddingPref.default)
                ReadingTextAlignPref.default.put(context, scope)
                ReadingLetterSpacingPref.put(context, scope, ReadingLetterSpacingPref.default)
                ReadingTextFontSizePref.put(context, scope, 18)
                ReadingImageRoundedCornersPref.put(context, scope, 0)
                ReadingImageHorizontalPaddingPref.put(context, scope, 0)
                ReadingImageMaximizePref.default.put(context, scope)
            }

            Paper -> {
                ReadingTitleBoldPref.ON.put(context, scope)
                ReadingTitleUpperCasePref.ON.put(context, scope)
                ReadingTitleAlignPref.Center.put(context, scope)
                ReadingSubheadBoldPref.ON.put(context, scope)
                ReadingSubheadUpperCasePref.ON.put(context, scope)
                ReadingSubheadAlignPref.Center.put(context, scope)
                ReadingTextBoldPref.default.put(context, scope)
                ReadingTextHorizontalPaddingPref.put(context, scope,
                    ReadingTextHorizontalPaddingPref.default)
                ReadingTextAlignPref.Center.put(context, scope)
                ReadingLetterSpacingPref.put(context, scope, ReadingLetterSpacingPref.default)
                ReadingTextFontSizePref.put(context, scope, 20)
                ReadingImageRoundedCornersPref.put(context, scope, 0)
                ReadingImageHorizontalPaddingPref.put(context, scope,
                    ReadingImageHorizontalPaddingPref.default)
                ReadingImageMaximizePref.default.put(context, scope)
            }

            Custom -> {
                ReadingTitleBoldPref.default.put(context, scope)
                ReadingTitleUpperCasePref.default.put(context, scope)
                ReadingTitleAlignPref.default.put(context, scope)
                ReadingSubheadBoldPref.default.put(context, scope)
                ReadingSubheadUpperCasePref.default.put(context, scope)
                ReadingSubheadAlignPref.default.put(context, scope)
                ReadingTextBoldPref.default.put(context, scope)
                ReadingTextHorizontalPaddingPref.put(context, scope,
                    ReadingTextHorizontalPaddingPref.default)
                ReadingTextAlignPref.default.put(context, scope)
                ReadingLetterSpacingPref.put(context, scope, ReadingLetterSpacingPref.default)
                ReadingTextFontSizePref.put(context, scope, ReadingTextFontSizePref.default)
                ReadingImageRoundedCornersPref.put(context, scope, ReadingImageRoundedCornersPref.default)
                ReadingImageHorizontalPaddingPref.put(context, scope,
                    ReadingImageHorizontalPaddingPref.default)
                ReadingImageMaximizePref.default.put(context, scope)
            }
        }
    }

    companion object {

        val default = MaterialYou
        val values = listOf(MaterialYou, Reeder, Paper, Custom)

        fun fromPreferences(preferences: Preferences): ReadingThemePref =
            when (preferences[DataStoreKeys.ReadingTheme.key]) {
                0 -> MaterialYou
                1 -> Reeder
                2 -> Paper
                3 -> Custom
                else -> default
            }
    }
}
