package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

sealed class FlowSemanticSearchPref(val value: Boolean) : Pref() {
    object ON : FlowSemanticSearchPref(true)
    object OFF : FlowSemanticSearchPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.FlowSemanticSearch,
                value
            )
        }
    }

    companion object {
        val default get() = ON
        val values get() = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.FlowSemanticSearch.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowSemanticSearchPref.not(): FlowSemanticSearchPref =
    when (value) {
        true -> FlowSemanticSearchPref.OFF
        false -> FlowSemanticSearchPref.ON
    }
