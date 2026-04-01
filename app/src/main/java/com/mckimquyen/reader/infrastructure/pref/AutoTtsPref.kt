package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class AutoTtsPref(val value: Boolean) : Pref() {
    object ON : AutoTtsPref(true)
    object OFF : AutoTtsPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.AutoTts,
                value
            )
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.AutoTts.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun AutoTtsPref.not(): AutoTtsPref =
    when (value) {
        true -> AutoTtsPref.OFF
        false -> AutoTtsPref.ON
    }
