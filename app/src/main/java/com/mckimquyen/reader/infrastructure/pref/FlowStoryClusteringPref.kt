package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

sealed class FlowStoryClusteringPref(val value: Boolean) : Pref() {
    object ON : FlowStoryClusteringPref(true)
    object OFF : FlowStoryClusteringPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.FlowStoryClustering,
                value
            )
        }
    }

    companion object {
        val default get() = ON
        val values get() = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.FlowStoryClustering.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowStoryClusteringPref.not(): FlowStoryClusteringPref =
    when (value) {
        true -> FlowStoryClusteringPref.OFF
        false -> FlowStoryClusteringPref.ON
    }
