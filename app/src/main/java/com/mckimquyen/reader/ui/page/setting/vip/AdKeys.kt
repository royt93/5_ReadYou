package com.mckimquyen.reader.ui.page.setting.vip

import android.util.Base64
import com.mckimquyen.reader.BuildConfig

/**
 * Centralize VIP secret + Privacy Policy URL cho VIP screen.
 *
 * - Ad unit ID + AppLovin SDK key đã đi qua [BuildConfig] (set ở `app/build.gradle` per buildType).
 * - VIP secret KHÔNG hardcode plain trong `.kt`: lưu Base64, decode lúc runtime
 *   (mức che giấu đã thống nhất — đủ chặn user thường peek decompiled APK).
 *
 * Lib AOS (`com.roy.sdkadbmob`) dùng **single secret** [com.roy.sdkadbmob.AdSdkConfig.vipKeySecret]:
 * [activateVipByKey] validate `key == vipKeySecret` rồi grant `days` do caller truyền.
 * App map nhiều "user-facing code" → số ngày qua [VipKeys], khi redeem luôn truyền
 * [VIP_SECRET] kèm số ngày tương ứng.
 */
object AdKeys {

    /** Privacy Policy URL — public, đọc thẳng từ BuildConfig. */
    const val PRIVACY_POLICY_URL: String = BuildConfig.PRIVACY_POLICY_URL

    /** Base64 của plain key 30-ngày (Section 0 của AD_PROMPT_AOS.MD). */
    private const val VIP_SECRET_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="

    /** Secret dùng cho [com.roy.sdkadbmob.AdSdkConfig.vipKeySecret]. */
    val VIP_SECRET: String by lazy {
        String(Base64.decode(VIP_SECRET_B64, Base64.NO_WRAP))
    }
}
