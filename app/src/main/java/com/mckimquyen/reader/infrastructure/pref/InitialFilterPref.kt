package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

sealed class InitialFilterPref(val value: Int) : Pref() {
    object Starred : InitialFilterPref(0)
    object Unread : InitialFilterPref(1)
    object All : InitialFilterPref(2)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.InitialFilter,
                value
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            Starred -> context.getString(R.string.starred)
            Unread -> context.getString(R.string.unread)
            All -> context.getString(R.string.all)
        }

    companion object {

        val default = All
        val values = listOf(Starred, Unread, All)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.InitialFilter.key]) {
                0 -> Starred
                1 -> Unread
                2 -> All
                else -> default
            }
    }
}
