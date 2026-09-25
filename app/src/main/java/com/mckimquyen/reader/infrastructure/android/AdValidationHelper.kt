package com.mckimquyen.reader.infrastructure.android

/**
 * Validates advertising identifiers to prevent revenue loss and policy violations
 * (such as using Google AdMob test IDs in production release builds).
 */
object AdValidationHelper {
    private const val GOOGLE_TEST_PUBLISHER_ID = "3940256099942544"

    fun isGoogleTestAdUnitId(adUnitId: String?): Boolean {
        if (adUnitId.isNullOrBlank()) return false
        return adUnitId.contains(GOOGLE_TEST_PUBLISHER_ID)
    }

    fun validateReleaseAdConfig(
        isDebug: Boolean,
        isEnableAdmob: Boolean,
        admobRewardedId: String,
    ): Boolean {
        if (!isDebug && isEnableAdmob && isGoogleTestAdUnitId(admobRewardedId)) {
            return false
        }
        return true
    }
}
