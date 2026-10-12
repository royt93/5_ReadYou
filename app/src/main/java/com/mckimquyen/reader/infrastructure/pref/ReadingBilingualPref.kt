package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bilingual Reader: show an on-device translation under each paragraph of the article.
 *
 * Off by default: enabling it downloads a ~30 MB language model on first use.
 */
sealed class ReadingBilingualPref(val value: Boolean) : Pref() {
    object ON : ReadingBilingualPref(true)
    object OFF : ReadingBilingualPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.ReadingBilingual, value)
        }
    }

    companion object {

        // A getter, not a field: reading OFF first would otherwise capture a half-initialised OFF (null).
        val default: ReadingBilingualPref get() = OFF

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.ReadingBilingual.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingBilingualPref.not(): ReadingBilingualPref =
    when (value) {
        true -> ReadingBilingualPref.OFF
        false -> ReadingBilingualPref.ON
    }
