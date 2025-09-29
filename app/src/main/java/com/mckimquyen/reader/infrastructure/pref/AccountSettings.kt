package com.mckimquyen.reader.infrastructure.pref

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.DataStoreKeys

// Accounts
val LocalSyncInterval = compositionLocalOf<SyncIntervalPref> { SyncIntervalPref.default }
val LocalSyncOnStart = compositionLocalOf<SyncOnStartPref> { SyncOnStartPref.default }
val LocalSyncOnlyOnWiFi = compositionLocalOf<SyncOnlyOnWiFiPref> { SyncOnlyOnWiFiPref.default }
val LocalSyncOnlyWhenCharging =
    compositionLocalOf<SyncOnlyWhenChargingPref> { SyncOnlyWhenChargingPref.default }
val LocalKeepArchived = compositionLocalOf<KeepArchivedPreference> { KeepArchivedPreference.default }
val LocalSyncBlockList = compositionLocalOf { SyncBlockListPref.default }

@Composable
fun AccountSettingsProvider(
    accountDao: AccountDao,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var currentAccountId by remember { mutableStateOf(1) } // Default account ID
    var accountSettings by remember { mutableStateOf<Account?>(null) }

    // Load account ID asynchronously to avoid ANR
    LaunchedEffect(Unit) {
        currentAccountId = withContext(Dispatchers.IO) {
            try {
                context.dataStore.data.map { prefs ->
                    prefs[DataStoreKeys.CurrentAccountId.key] ?: 1
                }.first()
            } catch (e: Exception) {
                Log.e("RLog", "Error loading account ID: $e")
                1 // Default account ID
            }
        }
    }

    // Load account settings using Flow collection
    accountSettings = accountDao.queryAccount(currentAccountId).collectAsStateValue(initial = null)

    CompositionLocalProvider(
        // Accounts - Use defaults until loaded to avoid blocking
        LocalSyncInterval provides (accountSettings?.syncInterval ?: SyncIntervalPref.default),
        LocalSyncOnStart provides (accountSettings?.syncOnStart ?: SyncOnStartPref.default),
        LocalSyncOnlyOnWiFi provides (accountSettings?.syncOnlyOnWiFi ?: SyncOnlyOnWiFiPref.default),
        LocalSyncOnlyWhenCharging provides (accountSettings?.syncOnlyWhenCharging
            ?: SyncOnlyWhenChargingPref.default),
        LocalKeepArchived provides (accountSettings?.keepArchived ?: KeepArchivedPreference.default),
        LocalSyncBlockList provides (accountSettings?.syncBlockList ?: SyncBlockListPref.default),
    ) {
        content()
    }
}
