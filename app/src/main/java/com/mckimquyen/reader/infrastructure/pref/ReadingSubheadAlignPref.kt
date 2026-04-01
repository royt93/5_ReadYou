package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class ReadingSubheadAlignPref(val value: Int) : Pref() {
    object Left : ReadingSubheadAlignPref(0)
    object Right : ReadingSubheadAlignPref(1)
    object Center : ReadingSubheadAlignPref(2)
    object Justify : ReadingSubheadAlignPref(3)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.ReadingSubheadAlign,
                value
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            Left -> context.getString(R.string.align_left)
            Right -> context.getString(R.string.align_right)
            Center -> context.getString(R.string.center_text)
            Justify -> context.getString(R.string.justify)
        }

    fun toTextAlign(): TextAlign =
        when (this) {
            Left -> TextAlign.Start
            Right -> TextAlign.End
            Center -> TextAlign.Center
            Justify -> TextAlign.Justify
        }

    companion object {

        val default = Left
        val values = listOf(Left, Right, Center, Justify)

        fun fromPreferences(preferences: Preferences): ReadingSubheadAlignPref =
            when (preferences[DataStoreKeys.ReadingSubheadAlign.key]) {
                0 -> Left
                1 -> Right
                2 -> Center
                3 -> Justify
                else -> default
            }
    }
}
