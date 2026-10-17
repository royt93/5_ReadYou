package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class ReadPositionSyncPref(val value: Boolean) : Pref() {
    object ON : ReadPositionSyncPref(true)
    object OFF : ReadPositionSyncPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.ReadPositionSync, value)
        }
    }

    companion object {
        val default: ReadPositionSyncPref get() = ON

        fun fromPreferences(preferences: Preferences): ReadPositionSyncPref =
            when (preferences[DataStoreKeys.ReadPositionSync.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadPositionSyncPref.not(): ReadPositionSyncPref =
    when (value) {
        true -> ReadPositionSyncPref.OFF
        false -> ReadPositionSyncPref.ON
    }
