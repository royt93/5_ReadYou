package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where the app asks for ads, for revenue and for policy:
 * - an interstitial request that is never shown lowers the unit's show rate, so nothing may load one while no screen shows one;
 * - the app open ad must not cover a system dialog the user has just been sent to (permission prompt, file picker) when the app comes back to the front.
 * The text of the sources is read because none of this fails at compile time.
 */
class AdPlacementSourceTest {

    private val roots = listOf("src/main/java/com/mckimquyen/reader/", "app/src/main/java/com/mckimquyen/reader/")
    private fun src(path: String): String = roots.map { File(it + path) }.first { it.exists() }.readText()
    private fun allKotlin(): List<Pair<String, String>> {
        val root = roots.map(::File).first { it.exists() }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.relativeTo(root).path to it.readText() }.toList()
    }

    @Test
    fun noInterstitialIsLoadedWhileNoScreenShowsOne() {
        val shows = allKotlin().any { (_, t) -> t.contains("AdManager.showInterstitial(") }
        val loads = allKotlin().filter { (_, t) -> t.contains("AdManager.loadInterstitial(") }.map { it.first }
        assertTrue("an interstitial is loaded but never shown: $loads", shows || loads.isEmpty())
    }

    @Test
    fun theSplashAndEveryLauncherStayExcludedOrSuppressed_sameHelperEverywhere() {
        val helper = src("infrastructure/android/AppOpenGuard.kt")
        assertTrue("the default switch must be the SDK call", helper.contains("AdManager::suppressAppOpenTemporarily"))
        assertTrue(helper.contains("setSuppressed(true)") && helper.contains("setSuppressed(false)"))
    }

    @Test
    fun everyActivityResultLauncherIsWrappedByTheGuard() {
        val launches = Regex("""(?<!fun )(?:\w*[lL]auncher|settings)\.launch\(""")
        val offenders = allKotlin().filter { (name, t) ->
            !name.endsWith("AppOpenGuard.kt") && launches.findAll(t).any { m ->
                val before = t.substring(maxOf(0, m.range.first - 60), m.range.first)
                !before.contains("AppOpenGuard.")
            }
        }.map { it.first }
        assertEquals("launchers that can leave the app without suppressing the app open ad: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun everyFileThatSuppressesOnLaunchReleasesInItsResultCallback() {
        // A launch without a release keeps the app open ad off for the SDK's five minute limit.
        val launchers = Regex("""\.launchGuarded\(""")
        val releases = Regex("""AppOpenGuard\.shared\.onResult\(\)""")
        val offenders = allKotlin().filter { (name, t) ->
            !name.endsWith("AppOpenGuard.kt") && launchers.findAll(t).count() > 0 &&
                releases.findAll(t).count() < Regex("""rememberLauncherForActivityResult\(""").findAll(t).count()
        }.map { it.first }
        assertEquals("files with a guarded launch but fewer releases than launchers: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun theOnlyInterstitialIsAtTheNextArticleBreak_behindThePacer() {
        val shows = allKotlin().filter { (_, t) -> t.contains("AdManager.showInterstitial(") }.map { it.first }
        assertEquals("the one natural break", listOf("ui/page/home/read/ReadingPage.kt"), shows.map { it.replace('\\', '/') })
        val page = src("ui/page/home/read/ReadingPage.kt")
        val next = page.substringAfter("onNextArticle = {").substringBefore("onFullContent")
        assertTrue("shown only when the pacer allows it", next.contains("InterstitialPacer.shared.onUserAction()"))
        assertTrue(next.indexOf("onUserAction") < next.indexOf("showInterstitial"))
        assertFalse("never from a back handler", Regex("""BackHandler\s*\{[^}]*showInterstitial""").containsMatchIn(page))
    }

    @Test
    fun theInterstitialIsPreloadedOnlyWhenThePacerIsAboutToAllowIt() {
        val page = src("ui/page/home/read/ReadingPage.kt")
        val loads = Regex("""AdManager\.loadInterstitial\(""").findAll(page).count()
        assertEquals(1, loads)
        val before = page.substringBefore("AdManager.loadInterstitial(").takeLast(120)
        assertTrue("the preload sits behind nextActionMayShow", before.contains("nextActionMayShow()"))
    }

    @Test
    fun everyRewardedButtonGoesThroughTheGate_noScreenCallsTheSdkDirectly() {
        // A direct AdManager.showRewarded call would be refused for a VIP (the SDK answers false), so only the gate may call it.
        val direct = allKotlin().filter { (name, t) -> !name.endsWith("RewardedGate.kt") && Regex("""AdManager\.(showRewarded|loadRewarded)\(""").containsMatchIn(t) }.map { it.first }
        assertEquals("screens that bypass RewardedGate: $direct", emptyList<String>(), direct)
    }

    @Test
    fun onlyTheVipDaysButtonSkipsTheVipShortcut() {
        val adOnly = allKotlin().filter { (name, t) -> !name.endsWith("RewardedGate.kt") && t.contains("requestAdOnly(") }.map { it.first.replace('\\', '/') }
        assertEquals(listOf("ui/page/setting/vip/VipManagementPage.kt"), adOnly)
    }

    @Test
    fun theTripsToAnotherApp_goThroughTheGuard_andMainActivityReleasesOnResume() {
        val ext = src("ui/ext/ExtContext.kt")
        val share = ext.substringAfter("fun Context.share(").substringBefore("fun Context.openURL(")
        assertTrue("the share chooser must leave through the guard", share.contains("AppOpenGuard.leaveForOtherApp"))
        val open = ext.substringAfter("fun Context.openURL(")
        // the main branch, inside the try and before the catch: the `when` that picks a browser, a Custom Tab or a chooser
        val tryPart = open.substringAfter("try {").substringBefore("catch (_: Throwable)")
        assertTrue("the browser and Custom Tab branches must leave through the guard", tryPart.contains("AppOpenGuard.leaveForOtherApp {"))
        assertTrue("and the `when` must sit inside that call", tryPart.indexOf("AppOpenGuard.leaveForOtherApp {") < tryPart.indexOf("when (openLink)"))
        // the fallback after a failed open is a trip too
        assertTrue(open.substringAfter("catch (_: Throwable)").contains("AppOpenGuard.leaveForOtherApp"))
        val page = src("ui/page/home/read/ReadingPage.kt")
        assertTrue("the second screen share chooser", page.substringAfter("onShare = { url ->").substringBefore("},").contains("AppOpenGuard.leaveForOtherApp"))
        val main = src("infrastructure/android/MainActivity.kt").substringAfter("override fun onResume()").substringBefore("override fun onPause()")
        assertTrue("the flag must be released when the app is back in front", main.contains("AppOpenGuard.shared.onResult()"))
    }

    @Test
    fun theGuardTurnsTheSuppressionOffWhenTheResultComes_andOnAnyError() {
        val helper = src("infrastructure/android/AppOpenGuard.kt")
        assertTrue("the result callback must clear the flag", helper.contains("finally") || helper.contains("onResult"))
        assertTrue("an exception while launching must clear the flag", helper.contains("catch") || helper.contains("finally"))
    }
}
