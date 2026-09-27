package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Whether an in-article video starts playing by itself.
 *
 * Autoplay always starts muted, so a video that begins on its own never makes noise the reader did
 * not ask for. Picture-in-Picture only engages once something is actually playing, so this also
 * decides whether leaving the app shrinks a video the reader never touched.
 */
sealed class ReadingVideoAutoplayPref(val value: Boolean) : Pref() {
    object ON : ReadingVideoAutoplayPref(true)
    object OFF : ReadingVideoAutoplayPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.ReadingVideoAutoplay, value)
        }
    }

    companion object {

        val default = ON

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.ReadingVideoAutoplay.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingVideoAutoplayPref.not(): ReadingVideoAutoplayPref =
    when (value) {
        true -> ReadingVideoAutoplayPref.OFF
        false -> ReadingVideoAutoplayPref.ON
    }
