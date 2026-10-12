package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bionic Reading: bold the leading ~40% of every word in the article body to help skimming.
 *
 * Off by default: it changes how every article looks, so it is something the reader opts into.
 */
sealed class ReadingBionicPref(val value: Boolean) : Pref() {
    object ON : ReadingBionicPref(true)
    object OFF : ReadingBionicPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.ReadingBionic, value)
        }
    }

    companion object {

        // A getter, not a field: reading OFF first would otherwise capture a half-initialised OFF (null).
        val default: ReadingBionicPref get() = OFF

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.ReadingBionic.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingBionicPref.not(): ReadingBionicPref =
    when (value) {
        true -> ReadingBionicPref.OFF
        false -> ReadingBionicPref.ON
    }
