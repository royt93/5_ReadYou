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
    fun theGuardTurnsTheSuppressionOffWhenTheResultComes_andOnAnyError() {
        val helper = src("infrastructure/android/AppOpenGuard.kt")
        assertTrue("the result callback must clear the flag", helper.contains("finally") || helper.contains("onResult"))
        assertTrue("an exception while launching must clear the flag", helper.contains("catch") || helper.contains("finally"))
    }
}
