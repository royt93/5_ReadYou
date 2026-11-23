package com.mckimquyen.reader.infrastructure.android

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AppPreferences

/** Remembers that the cleanup already ran, so later starts do not touch the ad SDK again. */
class FlagStore(private val prefs: SharedPreferences) {
    fun isDone(): Boolean = prefs.getBoolean(KEY_DONE, false)
    fun markDone() = prefs.edit().putBoolean(KEY_DONE, true).apply()

    private companion object {
        const val KEY_DONE = "legacy_auto_vip_cleaned"
    }
}

/**
 * Removes the old auto-VIP phone list from the ad SDK, once.
 *
 * The 2026-04-01 release wrote a fixed list of 21 test-phone ids into the SDK's preferences and the SDK treats any phone in it as VIP (no ads). The list is
 * read again at every start, so a phone on it never shows an ad. Only that list is removed: VIP from a token, a redeem code, a purchase or the trial is kept in
 * other keys and is not touched. The flag is set only when the removal succeeded, so a failure is retried at the next start.
 */
class LegacyAutoVipCleaner(
    private val readList: () -> List<String>,
    private val removeFromSdk: (List<String>) -> Unit,
    private val flag: FlagStore,
) {
    /** `true` when a list was found and removed on this call. */
    fun runOnce(): Boolean {
        if (flag.isDone()) return false
        val ids = try {
            readList()
        } catch (e: Exception) {
            Log.w(TAG, "could not read the old auto-VIP list, will retry", e)
            return false
        }
        if (ids.isEmpty()) {
            flag.markDone()
            return false
        }
        try {
            removeFromSdk(ids)
        } catch (e: Exception) {
            Log.w(TAG, "could not remove the old auto-VIP list, will retry", e)
            return false
        }
        flag.markDone()
        return true
    }

    companion object {
        private const val TAG = "roy93~Ad"
        private const val PREFS = "legacy_auto_vip_cleaner"

        /** The real wiring. Call it after the SDK finished its own start (the list is loaded there), never before. */
        fun forApp(context: Context) = LegacyAutoVipCleaner(
            readList = { AppPreferences.getInstance(context).getGAIDList() },
            removeFromSdk = { AdManager.deleteVIPMember(it) },
            flag = FlagStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)),
        )
    }
}
