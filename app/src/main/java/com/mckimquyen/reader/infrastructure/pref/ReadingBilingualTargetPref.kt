package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.domain.model.bilingual.BilingualLanguages
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Language the Bilingual Reader translates into: [BilingualLanguages.AUTO] (follow the app locale) or an
 * ML Kit language tag. A stored tag that is no longer supported reads back as [BilingualLanguages.AUTO].
 */
data class ReadingBilingualTargetPref(val value: String) : Pref() {

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.ReadingBilingualTarget, value)
        }
    }

    companion object {

        val default = ReadingBilingualTargetPref(BilingualLanguages.AUTO)

        fun fromPreferences(preferences: Preferences): ReadingBilingualTargetPref {
            val stored = preferences[DataStoreKeys.ReadingBilingualTarget.key]
            return if (stored != null && BilingualLanguages.isSupported(stored)) {
                ReadingBilingualTargetPref(stored)
            } else {
                default
            }
        }
    }
}
