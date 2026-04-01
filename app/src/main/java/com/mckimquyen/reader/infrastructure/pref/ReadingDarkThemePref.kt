package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

sealed class ReadingDarkThemePref(val value: Int) : Pref() {
    object UseAppTheme : ReadingDarkThemePref(0)
    object ON : ReadingDarkThemePref(1)
    object OFF : ReadingDarkThemePref(2)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.ReadingDarkTheme,
                value
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            UseAppTheme -> context.getString(R.string.use_app_theme)
            ON -> context.getString(R.string.on)
            OFF -> context.getString(R.string.off)
        }

    @Composable
    @ReadOnlyComposable
    fun isDarkTheme(): Boolean = when (this) {
        UseAppTheme -> LocalDarkTheme.current.isDarkTheme()
        ON -> true
        OFF -> false
    }

    companion object {

        val default = UseAppTheme
        val values = listOf(UseAppTheme, ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.ReadingDarkTheme.key]) {
                0 -> UseAppTheme
                1 -> ON
                2 -> OFF
                else -> default
            }
    }
}

@Stable
@Composable
@ReadOnlyComposable
operator fun ReadingDarkThemePref.not(): ReadingDarkThemePref =
    when (this) {
        ReadingDarkThemePref.UseAppTheme -> if (LocalDarkTheme.current.isDarkTheme()) {
            ReadingDarkThemePref.OFF
        } else {
            ReadingDarkThemePref.ON
        }

        ReadingDarkThemePref.ON -> ReadingDarkThemePref.OFF
        ReadingDarkThemePref.OFF -> ReadingDarkThemePref.ON
    }
