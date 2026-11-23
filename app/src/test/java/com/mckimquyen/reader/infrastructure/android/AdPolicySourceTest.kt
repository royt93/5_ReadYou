package com.mckimquyen.reader.infrastructure.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AdMob's "Disallowed interstitial implementations": no interstitial on app load or exit, at most one interstitial per two user actions (the Back
 * button counts), only at logical breaks between content. And a reward must come from a rewarded ad, never from watching an interstitial.
 *
 * Which ad shows where is not something a compiler checks, so the source of every screen that can show one is read as text.
 */
class AdPolicySourceTest {

    private val roots = listOf("src/main/java/com/mckimquyen/reader/", "app/src/main/java/com/mckimquyen/reader/")
    private fun src(path: String): String = roots.map { File(it + path) }.first { it.exists() }.readText()
    private fun allKotlin(): List<Pair<String, String>> {
        val root = roots.map(::File).first { it.exists() }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.relativeTo(root).path to it.readText() }.toList()
    }

    @Test
    fun settingsBack_neverShowsAnInterstitial() {
        val page = src("ui/page/setting/SettingsPage.kt")
        assertFalse("the system Back and the Back icon of Settings must not show an ad", page.contains("showInterstitial"))
    }

    @Test
    fun aiSettingsBack_neverShowsAnInterstitial() {
        assertFalse(src("ui/page/setting/ai/AiSettingsPage.kt").contains("showInterstitial"))
    }

    @Test
    fun noInterstitialIsShownFromABackHandlerOrAPopBackStackCallback() {
        val offenders = allKotlin().filter { (_, text) ->
            Regex("""showInterstitial\([^)]*\)\s*\{[^}]*popBackStack""").containsMatchIn(text) ||
                Regex("""BackHandler\s*\{[^}]*showInterstitial""").containsMatchIn(text)
        }.map { it.first }
        assertEquals("an interstitial on Back is disallowed: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun aRewardedAdThatEarnedNothing_isNotFollowedByAnInterstitial() {
        val offenders = allKotlin().filter { (_, text) ->
            Regex("""showRewarded\([^)]*\)\s*\{[^}]*earned[^}]*\}\s*else\s*\{[^}]*showInterstitial""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(text)
        }.map { it.first }
        assertEquals("never swap a refused reward for another ad: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun unlockingTheAmoledTheme_comesFromARewardedAd_notFromAnInterstitial() {
        val page = src("ui/page/setting/color/DarkThemePage.kt")
        assertTrue("the unlock must use a rewarded ad (through the gate)", page.contains("RewardedGate.shared.request("))
        assertFalse("watching an interstitial must not grant anything", page.contains("showInterstitial"))
    }

    @Test
    fun theUnlockOnlyHappensWhenTheRewardWasEarned() {
        val page = src("ui/page/setting/color/DarkThemePage.kt")
        val call = page.indexOf("RewardedGate.shared.request(")
        assertTrue("the rewarded call exists", call >= 0)
        val body = page.substring(call, minOf(page.length, call + 1400))
        val branch = Regex("""\{\s*earned\s*->\s*if\s*\(\s*earned\s*\)\s*\{""").find(body)
        assertTrue("the callback must branch on the real `earned` flag: $body", branch != null)
        val earnedBranchStart = branch!!.range.last
        val elseAt = body.indexOf("} else {", earnedBranchStart)
        val write = body.indexOf("AmoledUnlockedPref.ON.put", earnedBranchStart)
        assertTrue("the unlock is written", write > 0)
        assertTrue("and it is written inside the earned branch, before the else", elseAt < 0 || write < elseAt)
        if (elseAt >= 0) {
            val afterElse = body.substring(elseAt)
            assertFalse("nothing is unlocked in the not-earned branch", afterElse.contains("AmoledUnlockedPref.ON.put") || afterElse.contains("AmoledDarkThemePref.ON.put"))
        }
    }

    @Test
    fun commuteCastDeepDive_doesNotOfferAnInterstitialInPlaceOfTheReward() {
        assertFalse(src("ui/component/commute/CommuteCastSheet.kt").contains("showInterstitial"))
    }

    @Test
    fun vipPage_doesNotOfferAnInterstitialInPlaceOfTheReward() {
        assertFalse(src("ui/page/setting/vip/VipManagementPage.kt").contains("showInterstitial"))
    }
}
