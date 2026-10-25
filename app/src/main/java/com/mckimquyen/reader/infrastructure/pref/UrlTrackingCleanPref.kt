package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class UrlTrackingCleanPref(val value: Boolean) : Pref() {
    object ON : UrlTrackingCleanPref(true)
    object OFF : UrlTrackingCleanPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.UrlTrackingClean, value)
        }
    }

    companion object {
        val default: UrlTrackingCleanPref get() = ON

        fun fromPreferences(preferences: Preferences): UrlTrackingCleanPref =
            when (preferences[DataStoreKeys.UrlTrackingClean.key]) {
                false -> OFF
                else -> ON
            }
    }
}
