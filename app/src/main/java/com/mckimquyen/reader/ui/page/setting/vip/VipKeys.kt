package com.mckimquyen.reader.ui.page.setting.vip

import android.util.Base64

/**
 * Whitelist các user-facing VIP code → số ngày VIP.
 *
 * Plain key che giấu bằng Base64 (10.9 của AD_PROMPT_AOS.MD) — KHÔNG hardcode plain trong `.kt`.
 *
 * Lib chỉ có single [com.roy.sdkadbmob.AdSdkConfig.vipKeySecret] nên app validate input
 * tại đây trước, rồi truyền [AdKeys.VIP_SECRET] + số ngày tra được vào
 * [com.roy.sdkadbmob.AdManager.activateVipByKey].
 */
object VipKeys {

    /** Base64 của plain key 30-ngày. */
    private const val VIP_30D_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="

    /** Base64 của plain key 3-ngày. */
    private const val VIP_3D_B64 = "ZVE3QDkzTDBmITJZMjcwN3hOMDQwMjE5OTN1MEkjMmFL"

    val VIP_30D_KEY: String by lazy {
        String(Base64.decode(VIP_30D_B64, Base64.NO_WRAP))
    }
    val VIP_3D_KEY: String by lazy {
        String(Base64.decode(VIP_3D_B64, Base64.NO_WRAP))
    }

    /** Số ngày grant khi user xem hết rewarded ad. */
    const val REWARDED_DAYS = 3

    private val KEY_TO_DAYS: Map<String, Int> by lazy {
        mapOf(
            VIP_30D_KEY to 30,
            VIP_3D_KEY to 3,
        )
    }

    /** Trả số ngày nếu key hợp lệ, hoặc null. Case-sensitive — caller tự trim trước. */
    fun lookupDays(rawInput: String): Int? = KEY_TO_DAYS[rawInput.trim()]
}
