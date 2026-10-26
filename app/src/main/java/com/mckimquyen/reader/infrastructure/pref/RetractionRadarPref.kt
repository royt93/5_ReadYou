package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class RetractionRadarPref(val value: Boolean) : Pref() {
    object ON : RetractionRadarPref(true)
    object OFF : RetractionRadarPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.RetractionRadar, value)
        }
    }

    companion object {
        val default: RetractionRadarPref get() = ON

        fun fromPreferences(preferences: Preferences): RetractionRadarPref =
            when (preferences[DataStoreKeys.RetractionRadar.key]) {
                false -> OFF
                else -> ON
            }
    }
}
