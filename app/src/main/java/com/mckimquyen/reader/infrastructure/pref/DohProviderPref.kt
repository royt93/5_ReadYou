package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class DohProviderPref(val id: Int, val host: String, val endpoint: String, val bootstrapIp: String) : Pref() {
    object SYSTEM : DohProviderPref(0, "System", "", "")
    object CLOUDFLARE : DohProviderPref(1, "Cloudflare", "https://1.1.1.1/dns-query", "1.1.1.1")
    object ADGUARD : DohProviderPref(2, "AdGuard", "https://94.140.14.14/dns-query", "94.140.14.14")

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.DohProvider, id)
        }
    }

    companion object {
        val default: DohProviderPref get() = SYSTEM

        fun fromId(id: Int): DohProviderPref = when (id) {
            1 -> CLOUDFLARE
            2 -> ADGUARD
            else -> SYSTEM
        }

        fun fromPreferences(preferences: Preferences): DohProviderPref =
            fromId(preferences[DataStoreKeys.DohProvider.key] ?: default.id)
    }
}
