package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A user must be able to change the ad consent after the first answer (Google UMP policy, GDPR: withdrawing consent has to be as easy as giving it).
 * The Privacy page therefore carries one entry that reopens Google's privacy-options form through the SDK. These checks read the source, because the
 * page itself needs a running Activity and the real form is Google's.
 */
class AdConsentEntrySourceTest {

    private fun text(vararg candidates: String) = candidates.map(::File).first { it.exists() }.readText()
    private val page get() = text(
        "src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt",
        "app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt",
    )

    @Test
    fun privacyPage_hasAnEntryThatReopensTheConsentForm() {
        val p = page
        assertTrue(p.contains("R.string.ad_consent_manage_title"))
        assertTrue(p.contains("AdChoicesAction().open("))
    }

    @Test
    fun theEntryNeedsAnActivity_andDoesNothingWithoutOne() {
        val p = page
        val call = p.indexOf("AdChoicesAction().open(")
        // the Activity is looked up just above the SettingItem that holds the click
        val before = p.substring(maxOf(0, call - 900), call)
        assertTrue("the form needs a real Activity, never a context: $before", before.contains("findActivity()") || before.contains("activity"))
    }

    @Test
    fun theEntryAnswersWhereThereIsNoForm_throughTheTestedAction() {
        val p = page
        assertTrue("the press goes through AdChoicesAction", p.contains("AdChoicesAction().open("))
        assertTrue("a press that shows no form gets a message", p.contains("R.string.ad_consent_not_needed"))
        assertTrue("the page does not call the SDK form itself any more", !p.contains("AdManager.showConsentFormIfAvailable("))
    }

    @Test
    fun theActionAsksTheSdk_andOnlyLooksAtWhetherItShowedAForm() {
        val action = text(
            "src/main/java/com/mckimquyen/reader/infrastructure/android/AdChoicesAction.kt",
            "app/src/main/java/com/mckimquyen/reader/infrastructure/android/AdChoicesAction.kt",
        )
        assertTrue(action.contains("AdManager.showConsentFormIfAvailable(activity, done)"))
        // reading UMP's status ourselves would treat UNKNOWN wrongly and could hide the form from an EEA user
        assertTrue("the app must not decide from UMP's status", !action.contains("privacyOptionsRequirementStatus") && !action.contains("PrivacyOptionsRequirementStatus."))
    }

    @Test
    fun theNotNeededStringExistsInEveryLocale() {
        val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.exists() }
        val dirs = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.filter { File(it, "strings.xml").exists() }
        val missing = dirs.filter { d -> !File(d, "strings.xml").readText().contains("name=\"ad_consent_not_needed\"") }.map { it.name }
        assertEquals("locales without the message: $missing", emptyList<String>(), missing)
    }

    @Test
    fun theEntryStringExistsInEveryLocale() {
        val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.exists() }
        val dirs = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.filter { File(it, "strings.xml").exists() }
        val missing = dirs.filter { d -> !File(d, "strings.xml").readText().contains("name=\"ad_consent_manage_title\"") }.map { it.name }
        // night and v31 only hold themes, not strings
        assertEquals("locales without the entry title: $missing", emptyList<String>(), missing)
    }
}
