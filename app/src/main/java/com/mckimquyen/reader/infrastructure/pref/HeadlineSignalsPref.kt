package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Controls local clickbait/tone heuristics. No network or AI requests are made by this setting. */
sealed class HeadlineSignalsPref(val value: Boolean) : Pref() {
    object ON : HeadlineSignalsPref(true)
    object OFF : HeadlineSignalsPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.HeadlineSignals, value)
        }
    }

    companion object {
        val default: HeadlineSignalsPref get() = ON

        fun fromPreferences(preferences: Preferences): HeadlineSignalsPref =
            when (preferences[DataStoreKeys.HeadlineSignals.key]) {
                false -> OFF
                else -> ON
            }
    }
}
