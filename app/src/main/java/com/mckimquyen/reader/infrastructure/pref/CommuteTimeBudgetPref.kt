package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class CommuteTimeBudgetPref(val minutes: Int) : Pref() {
    object MIN_3 : CommuteTimeBudgetPref(3)
    object MIN_4 : CommuteTimeBudgetPref(4)
    object MIN_8 : CommuteTimeBudgetPref(8)
    object MIN_15 : CommuteTimeBudgetPref(15)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.CommuteTimeBudget,
                minutes
            )
        }
    }

    companion object {
        val default = MIN_4
        val values = listOf(MIN_3, MIN_4, MIN_8, MIN_15)

        fun fromMinutes(minutes: Int): CommuteTimeBudgetPref =
            values.firstOrNull { it.minutes == minutes } ?: default

        fun fromPreferences(preferences: Preferences): CommuteTimeBudgetPref =
            preferences[DataStoreKeys.CommuteTimeBudget.key]?.let { fromMinutes(it) } ?: default
    }
}
