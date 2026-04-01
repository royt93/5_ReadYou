package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.setting.acc.AccountViewModel

sealed class SyncOnlyWhenChargingPref(
    val value: Boolean,
) {

    object On : SyncOnlyWhenChargingPref(true)
    object Off : SyncOnlyWhenChargingPref(false)

    fun put(accountId: Int, viewModel: AccountViewModel) {
        viewModel.update(accountId) { syncOnlyWhenCharging = this@SyncOnlyWhenChargingPref }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toDesc(context: Context): String =
        when (this) {
            On -> context.getString(R.string.on)
            Off -> context.getString(R.string.off)
        }

    companion object {

        val default = Off
        val values = listOf(On, Off)
    }
}

operator fun SyncOnlyWhenChargingPref.not(): SyncOnlyWhenChargingPref =
    when (value) {
        true -> SyncOnlyWhenChargingPref.Off
        false -> SyncOnlyWhenChargingPref.On
    }
