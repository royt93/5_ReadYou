package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

sealed class AmoledUnlockedPref(val value: Boolean) : Pref() {
    object ON : AmoledUnlockedPref(true)
    object OFF : AmoledUnlockedPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.AmoledUnlocked,
                value
            )
        }
    }

    companion object {
        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.AmoledUnlocked.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun AmoledUnlockedPref.not(): AmoledUnlockedPref =
    when (value) {
        true -> AmoledUnlockedPref.OFF
        false -> AmoledUnlockedPref.ON
    }
