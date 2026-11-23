package com.mckimquyen.reader.ui.page.setting.vip

/**
 * What a typed VIP code can turn into. The SDK decides (ECDSA token, redeem code from `vipRedeemCodes`, never the old
 * plaintext secret); the app only decides what to tell the user, so the verdict is a pure function of three SDK answers.
 */
sealed interface VipRedeemResult {
    /** The code was accepted; [expiryMs] is the SDK expiry afterwards. */
    data class Granted(val expiryMs: Long) : VipRedeemResult

    /** A previous token is still being resolved; a second one would have no slot to report into. */
    data object Busy : VipRedeemResult

    /** The SDK refuses codes without a network (V-03); the code itself may be fine. */
    data object NeedsNetwork : VipRedeemResult

    /** Wrong, expired, already used, bound to another device/app, or blank. */
    data object Rejected : VipRedeemResult
}

/** The SDK calls the redeem needs, as a seam so the decision can be tested without the singleton. */
interface VipSdk {
    val isResolutionInFlight: Boolean
    fun isNetworkAvailable(): Boolean
    fun activate(code: String): Boolean
    fun expiryMs(): Long
}

object VipRedeem {

    /** Number of days the rewarded ad is worth; the single place this number lives. */
    const val REWARDED_DAYS = 3

    fun redeem(sdk: VipSdk, rawInput: String): VipRedeemResult {
        val code = rawInput.trim()
        if (code.isEmpty()) return VipRedeemResult.Rejected
        if (sdk.isResolutionInFlight) return VipRedeemResult.Busy
        // The SDK would answer `false` for "no network" exactly like for "wrong code"; ask first so the user gets the real reason.
        if (!sdk.isNetworkAvailable()) return VipRedeemResult.NeedsNetwork
        return if (sdk.activate(code)) VipRedeemResult.Granted(sdk.expiryMs()) else VipRedeemResult.Rejected
    }
}
