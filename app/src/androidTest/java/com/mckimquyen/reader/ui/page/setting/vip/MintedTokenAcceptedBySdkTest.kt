package com.mckimquyen.reader.ui.page.setting.vip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A token minted by myKeyStore/com.mckimquyen.reader/mint_vip_token.py (Python + openssl) must be accepted by the real SDK.
 *
 * The tokens are NOT in the source. The SDK remembers a used token on the phone for good (`clearVipByKey` does not forget it), so a fixed token
 * would pass once and then fail forever. They come in through instrumentation arguments, minted fresh for each run with a THROWAWAY key pair
 * (never the real one): `adb shell am instrument -w -e pub .. -e valid .. -e valid_replay .. -e other_phone .. -e expired .. -e tamper ..`.
 * Without the arguments the tests are skipped. Needs a network (the SDK refuses codes offline).
 */
@RunWith(AndroidJUnit4::class)
class MintedTokenAcceptedBySdkTest {

    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun arg(name: String): String? = args.getString(name)?.takeIf { it.isNotBlank() }

    @Before
    fun setUp() {
        val pub = arg("pub")
        assumeTrue("minted tokens not supplied", pub != null)
        AdManager.setConfig(AdSdkConfig(isEnableAdmob = false, isDebug = true, vipKeySecret = AdKeys.VIP_SECRET, vipTokenPublicKey = pub!!))
        runCatching { AdManager.clearVipByKey() }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
    }

    @Test
    fun aTokenFromTheTool_isAccepted() {
        assertFalse(AdManager.isVipByKeyActive())
        assertTrue(AdManager.activateVipByToken(context, arg("valid")!!))
        assertTrue(AdManager.isVipByKeyActive())
    }

    @Test
    fun theSameTokenTwice_isRefusedTheSecondTime() {
        val token = arg("valid_replay")!!
        assertTrue(AdManager.activateVipByToken(context, token))
        runCatching { AdManager.clearVipByKey() }
        assertFalse("replay protection", AdManager.activateVipByToken(context, token))
        assertFalse(AdManager.isVipByKeyActive())
    }

    @Test
    fun aTokenBoundToAnotherPhone_isRefused() {
        assertFalse(AdManager.activateVipByToken(context, arg("other_phone")!!))
        assertFalse(AdManager.isVipByKeyActive())
    }

    @Test
    fun anExpiredToken_isRefused() {
        assertFalse(AdManager.activateVipByToken(context, arg("expired")!!))
        assertFalse(AdManager.isVipByKeyActive())
    }

    @Test
    fun aChangedCharacter_isRefused() {
        val token = arg("tamper")!!
        val tail = token.takeLast(3)
        assertFalse(AdManager.activateVipByToken(context, token.dropLast(3) + (if (tail == "AAA") "BBB" else "AAA")))
        assertFalse(AdManager.isVipByKeyActive())
    }
}
