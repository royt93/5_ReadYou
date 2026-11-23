package com.mckimquyen.reader.ui.page.setting.vip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.BuildConfig
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The redeem codes the owner hands out ("thẻ cào") must work end to end: typed into the VIP screen, read from the private properties file into
 * BuildConfig, parsed by [AdKeys], and accepted by the real SDK for the right number of days.
 *
 * The codes themselves are never in the source: they arrive as instrumentation arguments (`-e code30 .. -e code3 ..`), the same way the app gets them
 * from the private file. A code is remembered by the SDK for good, so the real ones can be used once per phone; the test therefore redeems them and
 * the caller restores the SDK prefs afterwards. Without the arguments the tests are skipped. Needs a network.
 */
@RunWith(AndroidJUnit4::class)
class RedeemCodesAcceptedBySdkTest {

    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val code30 get() = args.getString("code30")
    private val code3 get() = args.getString("code3")

    @Before
    fun setUp() {
        assumeTrue("owner codes not supplied", !code30.isNullOrBlank() && !code3.isNullOrBlank())
        AdManager.setConfig(
            AdSdkConfig(isEnableAdmob = false, isDebug = true, vipKeySecret = AdKeys.VIP_SECRET, vipTokenPublicKey = AdKeys.VIP_TOKEN_PUBLIC_KEY, vipRedeemCodes = AdKeys.VIP_REDEEM_CODES)
        )
        runCatching { AdManager.clearVipByKey() }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
    }

    @Test
    fun theBuildCarriesExactlyTheTwoOwnerCodes() {
        val codes = AdKeys.VIP_REDEEM_CODES
        assertEquals("the build must carry the two owner codes and nothing else", setOf(code30, code3), codes.keys)
        assertEquals(30, codes[code30])
        assertEquals(3, codes[code3])
        assertTrue("a release build gets the same codes", BuildConfig.VIP_REDEEM_CODES.isNotBlank())
    }

    /**
     * One ordered scenario, not independent tests: the SDK keeps every redeemed code for good (clearing VIP does not forget it) and slows down after
     * wrong tries, so each real code can be redeemed once per phone and the wrong-code check has to come last.
     */
    @Test
    fun theTwoOwnerCodes_workOnceEach_addUp_andOnlyThen() {
        // a code with spaces and a newline around it, as a user may paste it
        assertFalse(AdManager.isVipByKeyActive())
        assertTrue(AdManager.activateVipByToken(context, "  ${code3!!}  \n"))
        val afterThree = AdManager.getVipByKeyExpiry() - System.currentTimeMillis()
        assertTrue("about 3 days, was ${afterThree / DAY_MS} days", afterThree in (2 * DAY_MS)..(3 * DAY_MS + 60_000))

        // the 30 day code adds on top of the 3 days
        assertTrue(AdManager.activateVipByToken(context, code30!!))
        val afterBoth = AdManager.getVipByKeyExpiry() - System.currentTimeMillis()
        assertTrue("3 + 30 days, was ${afterBoth / DAY_MS} days", afterBoth in (32 * DAY_MS)..(33 * DAY_MS + 60_000))
        assertTrue(AdManager.isVipByKeyActive())

        // used codes are refused, even after the VIP was cleared
        runCatching { AdManager.clearVipByKey() }
        assertFalse("the used-code ledger must refuse the 3 day code a second time", AdManager.activateVipByToken(context, code3!!))
        assertFalse("and the 30 day code", AdManager.activateVipByToken(context, code30!!))
        assertFalse(AdManager.isVipByKeyActive())
    }

    @Test
    fun aCodeWithOneWrongCharacter_isRefused() {
        val wrong = code30!!.dropLast(1) + (if (code30!!.last() == 'x') 'y' else 'x')
        assertFalse(AdManager.activateVipByToken(context, wrong))
        assertFalse(AdManager.isVipByKeyActive())
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
