package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for [FIX-08]: ensuring Google AdMob test IDs are flagged and prevented in release builds.
 */
class AdValidationHelperTest {

    private val googleTestRewardedId = "ca-app-pub-3940256099942544/5224354917"
    private val productionRewardedId = "ca-app-pub-3612191981543807/5224354917"

    @Test
    fun isGoogleTestAdUnitId_detectsGoogleTestPublisher() {
        assertTrue(AdValidationHelper.isGoogleTestAdUnitId(googleTestRewardedId))
        assertFalse(AdValidationHelper.isGoogleTestAdUnitId(productionRewardedId))
        assertFalse(AdValidationHelper.isGoogleTestAdUnitId(null))
        assertFalse(AdValidationHelper.isGoogleTestAdUnitId(""))
    }

    @Test
    fun validateReleaseAdConfig_allowsTestIdInDebug() {
        val isValid = AdValidationHelper.validateReleaseAdConfig(
            isDebug = true,
            isEnableAdmob = true,
            admobRewardedId = googleTestRewardedId,
        )
        assertTrue("Debug builds may use test IDs", isValid)
    }

    @Test
    fun validateReleaseAdConfig_rejectsTestIdInProductionRelease() {
        val isValid = AdValidationHelper.validateReleaseAdConfig(
            isDebug = false,
            isEnableAdmob = true,
            admobRewardedId = googleTestRewardedId,
        )
        assertFalse("Production release builds must reject Google test IDs", isValid)
    }

    @Test
    fun validateReleaseAdConfig_allowsProductionIdInProductionRelease() {
        val isValid = AdValidationHelper.validateReleaseAdConfig(
            isDebug = false,
            isEnableAdmob = true,
            admobRewardedId = productionRewardedId,
        )
        assertTrue("Production release builds must allow real IDs", isValid)
    }
}
