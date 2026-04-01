package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.Keep
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

@Keep
data class OpenLinkSpecificBrowserPref(
    val packageName: String?
    ) : Pref() {

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.OpenLinkAppSpecificBrowser,
                packageName.orEmpty()
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String {
        val pm = context.packageManager
        return runCatching {
            pm.run {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getApplicationInfo(
                        this@OpenLinkSpecificBrowserPref.packageName!!,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                } else {
                    getApplicationInfo(this@OpenLinkSpecificBrowserPref.packageName!!, 0)
                }
            }
        }.map {
            it.loadLabel(pm)
        }.getOrDefault(context.getString(R.string.open_link_specific_browser_not_selected)).let {
            context.getString(R.string.specific_browser_name, it)
        }
    }

    companion object {
        val default = OpenLinkSpecificBrowserPref(null)
        fun fromPreferences(preferences: Preferences): OpenLinkSpecificBrowserPref {
            val packageName = preferences[DataStoreKeys.OpenLinkAppSpecificBrowser.key]
            return OpenLinkSpecificBrowserPref(packageName)
        }
    }
}
