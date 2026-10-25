package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class HtmlAntiTrackerPref(val value: Boolean) : Pref() {
    object ON : HtmlAntiTrackerPref(true)
    object OFF : HtmlAntiTrackerPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.HtmlAntiTracker, value)
        }
    }

    companion object {
        val default: HtmlAntiTrackerPref get() = ON

        fun fromPreferences(preferences: Preferences): HtmlAntiTrackerPref =
            when (preferences[DataStoreKeys.HtmlAntiTracker.key]) {
                false -> OFF
                else -> ON
            }
    }
}
