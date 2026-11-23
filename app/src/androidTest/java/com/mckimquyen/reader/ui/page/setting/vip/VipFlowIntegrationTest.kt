package com.mckimquyen.reader.ui.page.setting.vip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real VIP activation through the SDK (no mock). Needs a network (the SDK refuses codes offline) and runs with the debug
 * config, so it never touches ad inventory.
 */
@RunWith(AndroidJUnit4::class)
class VipFlowIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val redeemCodes = mapOf("INTEG-TEST-30" to 30)

    @Before
    fun setUp() {
        AdManager.setConfig(
            AdSdkConfig(
                isEnableAdmob = false,
                isDebug = true,
                vipKeySecret = AdKeys.VIP_SECRET,
                vipTokenPublicKey = AdKeys.VIP_TOKEN_PUBLIC_KEY,
                vipRedeemCodes = redeemCodes,
            )
        )
        runCatching { AdManager.clearVipByKey() }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
    }

    @Test
    fun redeemCode_activatesVipForItsDays() {
        assertFalse(AdManager.isVipByKeyActive())
        assertTrue(AdManager.activateVipByToken(context, "INTEG-TEST-30"))
        assertTrue(AdManager.isVipByKeyActive())
        val remainingMs = AdManager.getVipByKeyExpiry() - System.currentTimeMillis()
        assertTrue(remainingMs in (29L * 24 * 3600_000)..(30L * 24 * 3600_000 + 60_000))
    }

    @Test
    fun wrongCode_isRefused() {
        assertFalse(AdManager.activateVipByToken(context, "SAI-KEY-999"))
        assertFalse(AdManager.isVipByKeyActive())
    }

    @Test
    fun theOldPlaintextSecret_isNoLongerAKey() {
        assertFalse(AdManager.activateVipByKey(context, AdKeys.VIP_SECRET, 30))
        assertFalse(AdManager.isVipByKeyActive())
    }

    @Test
    fun rewardedGrant_addsDaysWithoutAnyKey() {
        assertTrue(AdManager.grantVipDays(context, VipRedeem.REWARDED_DAYS))
        assertTrue(AdManager.isVipByKeyActive())
    }
}
