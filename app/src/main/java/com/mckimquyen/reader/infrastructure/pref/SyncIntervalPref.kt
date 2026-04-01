package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.sv.SyncWorker
import com.mckimquyen.reader.ui.page.setting.acc.AccountViewModel
import java.util.concurrent.TimeUnit

sealed class SyncIntervalPref(
    val value: Long,
) {

    object Manually : SyncIntervalPref(0L)
    object Every15Minutes : SyncIntervalPref(15L)
    object Every30Minutes : SyncIntervalPref(30L)
    object Every1Hour : SyncIntervalPref(60L)
    object Every2Hours : SyncIntervalPref(120L)
    object Every3Hours : SyncIntervalPref(180L)
    object Every6Hours : SyncIntervalPref(360L)
    object Every12Hours : SyncIntervalPref(720L)
    object Every1Day : SyncIntervalPref(1440L)

    fun put(accountId: Int, viewModel: AccountViewModel) {
        viewModel.update(accountId) { syncInterval = this@SyncIntervalPref }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            Manually -> context.getString(R.string.manually)
            Every15Minutes -> context.getString(R.string.every_15_minutes)
            Every30Minutes -> context.getString(R.string.every_30_minutes)
            Every1Hour -> context.getString(R.string.every_1_hour)
            Every2Hours -> context.getString(R.string.every_2_hours)
            Every3Hours -> context.getString(R.string.every_3_hours)
            Every6Hours -> context.getString(R.string.every_6_hours)
            Every12Hours -> context.getString(R.string.every_12_hours)
            Every1Day -> context.getString(R.string.every_1_day)
        }

    fun toPeriodicWorkRequestBuilder(): PeriodicWorkRequest.Builder =
        PeriodicWorkRequestBuilder<SyncWorker>(value, TimeUnit.MINUTES)

    companion object {

        val default = Every30Minutes
        val values = listOf(
            Manually,
            Every15Minutes,
            Every30Minutes,
            Every1Hour,
            Every2Hours,
            Every3Hours,
            Every6Hours,
            Every12Hours,
            Every1Day,
        )
    }
}
