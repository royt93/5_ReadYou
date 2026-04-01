package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class ReadingTitleAlignPref(val value: Int) : Pref() {
    object Left : ReadingTitleAlignPref(0)
    object Right : ReadingTitleAlignPref(1)
    object Center : ReadingTitleAlignPref(2)
    object Justify : ReadingTitleAlignPref(3)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.ReadingTitleAlign,
                value
            )
        }
    }

    @Stable
    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            Left -> context.getString(R.string.align_left)
            Right -> context.getString(R.string.align_right)
            Center -> context.getString(R.string.center_text)
            Justify -> context.getString(R.string.justify)
        }

    @Stable
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

        fun fromPreferences(preferences: Preferences): ReadingTitleAlignPref =
            when (preferences[DataStoreKeys.ReadingTitleAlign.key]) {
                0 -> Left
                1 -> Right
                2 -> Center
                3 -> Justify
                else -> default
            }
    }
}
