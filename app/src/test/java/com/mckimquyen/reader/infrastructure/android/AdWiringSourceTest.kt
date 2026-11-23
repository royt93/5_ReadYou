package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level guards for the ad wiring. Each of these was a real way to lose money or to send invalid traffic, and none of
 * them fails at compile time, so the text of the files is the thing under test.
 */
class AdWiringSourceTest {

    private fun src(path: String): String {
        val roots = listOf("src/main/", "app/src/main/")
        return roots.map { File(it + path) }.first { it.exists() }.readText()
    }

    private val rapp get() = src("java/com/mckimquyen/reader/RApp.kt")

    @Test
    fun `test devices are registered for debug and release alike`() {
        val text = rapp
        val call = text.indexOf("AdManager.setTestDeviceIds(")
        assertTrue("setTestDeviceIds must be called", call > 0)
        val before = text.substring(maxOf(0, call - 400), call)
        assertFalse("setTestDeviceIds must not sit behind a DEBUG check", before.contains("if (BuildConfig.DEBUG)") && !before.contains("}"))
        val line = text.lines().first { it.contains("AdManager.setTestDeviceIds(") }
        assertFalse(line.contains("DEBUG"))
    }

    @Test
    fun `test devices are registered right after setConfig`() {
        val text = rapp
        assertTrue(text.indexOf("AdManager.setConfig(adConfig)") < text.indexOf("AdManager.setTestDeviceIds("))
    }

    @Test
    fun `paid listener is set in Application, never from an Activity`() {
        assertTrue(rapp.contains("AdManager.paidEventListener ="))
    }

    @Test
    fun `app open stays off the splash`() {
        assertTrue(rapp.contains("appOpenExcludedActivities = listOf(SplashActivity::class.java)"))
    }

    @Test
    fun `VIP keys come from the private file, not from source`() {
        val text = rapp
        assertTrue(text.contains("vipTokenPublicKey      = AdKeys.VIP_TOKEN_PUBLIC_KEY"))
        assertTrue(text.contains("vipRedeemCodes         = AdKeys.VIP_REDEEM_CODES"))
        assertFalse("the legacy plaintext path must stay off", text.contains("allowLegacyPlaintextVipKey = true"))
    }

    @Test
    fun `rewarded VIP goes through grantVipDays and never through the plaintext secret`() {
        val page = src("java/com/mckimquyen/reader/ui/page/setting/vip/VipManagementPage.kt")
        assertTrue(page.contains("AdManager.grantVipDays("))
        assertFalse(page.contains("activateVipByKey("))
        assertFalse(page.contains("AdKeys.VIP_SECRET"))
    }

    @Test
    fun `splash has no competing timer around consent`() {
        val splash = src("java/com/mckimquyen/reader/infrastructure/android/SplashActivity.kt")
        assertFalse(splash.contains("preSdkTimeoutJob"))
        assertFalse(splash.contains("delay(8_000L)"))
        assertTrue(splash.contains("splashFlowStarted.compareAndSet(false, true)"))
    }

    @Test
    fun `manifest takes the AdMob app id from the build type`() {
        val manifest = src("AndroidManifest.xml")
        assertTrue(manifest.contains("android:value=\"\${admobAppId}\""))
        assertFalse("no AdMob app id may be written into the manifest", Regex("ca-app-pub-\\d+~\\d+").containsMatchIn(manifest))
        assertTrue(manifest.contains("<attribution android:tag=\"ads\""))
    }

    @Test
    fun `ad SDK ledger is excluded from every backup path`() {
        val rules = src("res/xml/backup_rules.xml")
        assertTrue(rules.contains("path=\"loitp_admob.xml\""))
        val extraction = src("res/xml/data_extraction_rules.xml")
        val cloud = extraction.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = extraction.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        assertTrue(cloud.contains("loitp_admob.xml"))
        assertTrue(transfer.contains("loitp_admob.xml"))
    }

    @Test
    fun `no ad id or secret is written into the gradle file`() {
        val gradle = File(listOf("build.gradle", "app/build.gradle").first { File(it).exists() }).readText()
        assertFalse(Regex("ca-app-pub-(?!3940256099942544)\\d+").containsMatchIn(gradle))
        assertFalse(Regex("[A-Za-z0-9_]{60,}").containsMatchIn(gradle))
    }
}
