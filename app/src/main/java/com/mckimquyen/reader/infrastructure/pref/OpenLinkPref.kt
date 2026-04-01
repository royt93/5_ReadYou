package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put

sealed class OpenLinkPref(val value: Int) : Pref() {
    object AutoPreferCustomTabs : OpenLinkPref(0)
    object AutoPreferDefaultBrowser : OpenLinkPref(1)
    object CustomTabs : OpenLinkPref(2)
    object DefaultBrowser : OpenLinkPref(3)
    object SpecificBrowser: OpenLinkPref(4)
    object AlwaysAsk: OpenLinkPref(5)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.OpenLink,
                value
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            AutoPreferCustomTabs -> context.getString(R.string.auto_customtabs)
            AutoPreferDefaultBrowser -> context.getString(R.string.auto_default_browser)
            CustomTabs -> context.getString(R.string.custom_tabs)
            DefaultBrowser -> context.getString(R.string.default_browser)
            SpecificBrowser -> context.getString(R.string.specific_browser)
            AlwaysAsk -> context.getString(R.string.always_ask)
        }

    companion object {

        val default = CustomTabs
        val values = listOf(AutoPreferCustomTabs, AutoPreferDefaultBrowser, CustomTabs, DefaultBrowser, SpecificBrowser, AlwaysAsk)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.OpenLink.key]) {
                0 -> AutoPreferCustomTabs
                1 -> AutoPreferDefaultBrowser
                2 -> CustomTabs
                3 -> DefaultBrowser
                4 -> SpecificBrowser
                5 -> AlwaysAsk
                else -> AutoPreferCustomTabs
            }
    }
}
