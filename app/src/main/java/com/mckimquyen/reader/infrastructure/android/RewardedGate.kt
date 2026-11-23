package com.mckimquyen.reader.infrastructure.android

import android.app.Activity
import android.content.Context
import com.roy.sdkadbmob.AdManager

/**
 * One entry for every "watch an ad to get X" button.
 *
 * The SDK refuses [AdManager.showRewarded] for a VIP user (callback false), so a VIP who pressed such a button got "ad not available" for something the
 * VIP status is meant to give. A VIP now gets the reward at once, without an ad; everyone else goes through the SDK exactly as before.
 * The reward is granted by the caller's callback, once, with `true` only when it is really due.
 */
class RewardedGate(
    private val isVip: () -> Boolean = AdManager::isVipByKeyActive,
    private val show: (Activity, (Boolean) -> Unit) -> Unit = { activity, done -> AdManager.showRewarded(activity, done) },
    private val preload: (Context) -> Unit = AdManager::loadRewarded,
) {
    /** Shows the ad, or skips it for a VIP; [onResult] gets true when the reward is due. */
    fun request(activity: Activity, onResult: (Boolean) -> Unit) {
        if (isVip()) onResult(true) else show(activity, onResult)
    }

    /**
     * Always goes to the SDK, never skipped for a VIP. Only for the one button whose reward is VIP itself ("watch an ad, get 3 days of VIP"):
     * skipping the ad there would hand out VIP days for nothing.
     */
    fun requestAdOnly(activity: Activity, onResult: (Boolean) -> Unit) = show(activity, onResult)

    /** Warms the next ad when a screen with a rewarded button opens; nothing to load for a VIP. */
    fun warmUp(context: Context) {
        if (!isVip()) preload(context)
    }

    companion object {
        val shared: RewardedGate by lazy { RewardedGate() }
    }
}
