package com.mckimquyen.reader.ui.page.setting.vip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VipRedeemTest {

    private class FakeSdk(
        override var isResolutionInFlight: Boolean = false,
        var network: Boolean = true,
        var accepts: Boolean = true,
        var expiry: Long = 1_000L,
    ) : VipSdk {
        val activated = mutableListOf<String>()
        override fun isNetworkAvailable() = network
        override fun activate(code: String): Boolean {
            activated += code
            return accepts
        }
        override fun expiryMs() = expiry
    }

    @Test
    fun `an accepted code is trimmed, sent once and reports the SDK expiry`() {
        val sdk = FakeSdk(expiry = 42L)
        assertEquals(VipRedeemResult.Granted(42L), VipRedeem.redeem(sdk, "  ABC123 \n"))
        assertEquals(listOf("ABC123"), sdk.activated)
    }

    @Test
    fun `a refused code is Rejected`() {
        assertEquals(VipRedeemResult.Rejected, VipRedeem.redeem(FakeSdk(accepts = false), "WRONG"))
    }

    @Test
    fun `blank input never reaches the SDK`() {
        val sdk = FakeSdk()
        assertEquals(VipRedeemResult.Rejected, VipRedeem.redeem(sdk, "   "))
        assertEquals(VipRedeemResult.Rejected, VipRedeem.redeem(sdk, ""))
        assertTrue(sdk.activated.isEmpty())
    }

    @Test
    fun `offline is reported as NeedsNetwork and not as a wrong code`() {
        val sdk = FakeSdk(network = false)
        assertEquals(VipRedeemResult.NeedsNetwork, VipRedeem.redeem(sdk, "ABC123"))
        assertTrue("the SDK must not be asked while offline", sdk.activated.isEmpty())
    }

    @Test
    fun `a token still being resolved blocks a second one`() {
        val sdk = FakeSdk(isResolutionInFlight = true)
        assertEquals(VipRedeemResult.Busy, VipRedeem.redeem(sdk, "ABC123"))
        assertTrue(sdk.activated.isEmpty())
    }

    @Test
    fun `the rewarded ad is worth three days`() {
        assertEquals(3, VipRedeem.REWARDED_DAYS)
    }
}
