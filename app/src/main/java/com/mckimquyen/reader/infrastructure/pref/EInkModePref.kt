package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class EInkModePref(val value: Boolean) : Pref() {
    object ON : EInkModePref(true)
    object OFF : EInkModePref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.EInkMode, value)
        }
    }

    companion object {
        val default: EInkModePref get() = OFF

        fun fromPreferences(preferences: Preferences): EInkModePref =
            when (preferences[DataStoreKeys.EInkMode.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun EInkModePref.not(): EInkModePref =
    when (value) {
        true -> EInkModePref.OFF
        false -> EInkModePref.ON
    }
