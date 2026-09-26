package com.mckimquyen.reader.ui.page.watchdog

import androidx.lifecycle.ViewModel
import com.mckimquyen.reader.domain.model.watchdog.WatchdogAlert
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel cho màn hình Watchdog Alert Inbox (REEL-06).
 * Chỉ đọc/ghi qua [WatchdogManager]; không giữ Context/Activity/LazyListState.
 */
@HiltViewModel
class WatchdogInboxViewModel @Inject constructor(
    private val watchdogManager: WatchdogManager,
) : ViewModel() {

    val alerts: StateFlow<List<WatchdogAlert>> = watchdogManager.alerts

    fun markAsRead(alertId: String) = watchdogManager.markAlertAsRead(alertId)

    fun markAllAsRead() = watchdogManager.markAllAlertsAsRead()

    fun clearAll() = watchdogManager.clearAlerts()
}
