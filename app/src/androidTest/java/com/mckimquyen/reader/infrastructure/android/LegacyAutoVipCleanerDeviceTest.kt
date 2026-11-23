package com.mckimquyen.reader.infrastructure.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.ui.page.setting.vip.AdKeys
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.roy.sdkadbmob.AppPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a phone with the real SDK: removing the old auto-VIP list must not take away VIP the user earned honestly (here a rewarded-style grant), and must
 * remove exactly the ids that were in the list.
 */
@RunWith(AndroidJUnit4::class)
class LegacyAutoVipCleanerDeviceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val oldIds = (1..21).map { "00000000-0000-4000-8000-%012d".format(it) }

    @Before
    fun setUp() {
        AdManager.setConfig(AdSdkConfig(isEnableAdmob = false, isDebug = true, vipKeySecret = AdKeys.VIP_SECRET, vipTokenPublicKey = AdKeys.VIP_TOKEN_PUBLIC_KEY))
        runCatching { AdManager.clearVipByKey() }
    }

    @After
    fun tearDown() {
        runCatching { AdManager.clearVipByKey() }
        runCatching { AdManager.deleteVIPMember(oldIds) }
    }

    @Test
    fun honestVipSurvivesTheCleanup_andOnlyTheListIsRemoved() {
        AdManager.addVIPMember(oldIds)
        assertEquals(oldIds.toSet(), AppPreferences.getInstance(context).getGAIDList().toSet().intersect(oldIds.toSet()))
        assertTrue(AdManager.grantVipDays(context, 3))
        assertTrue(AdManager.isVipByKeyActive())

        val flag = FlagStore(context.getSharedPreferences("cleaner_device_test", android.content.Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        val cleaner = LegacyAutoVipCleaner(
            readList = { AppPreferences.getInstance(context).getGAIDList().filter { it in oldIds } },
            removeFromSdk = { AdManager.deleteVIPMember(it) },
            flag = flag,
        )
        assertTrue(cleaner.runOnce())

        assertTrue("the old list is gone", AppPreferences.getInstance(context).getGAIDList().none { it in oldIds })
        assertTrue("VIP earned honestly is kept", AdManager.isVipByKeyActive())
        assertFalse("the cleanup does not run twice", cleaner.runOnce())
    }
}
