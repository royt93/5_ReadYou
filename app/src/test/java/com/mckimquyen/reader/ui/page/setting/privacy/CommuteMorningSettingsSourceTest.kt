package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteMorningSettingsSourceTest {

    private fun code(path: String) = File("src/main/java/com/mckimquyen/reader/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val page = code("ui/page/setting/privacy/PrivacySettingsPage.kt")
    private val rows = code("ui/page/setting/privacy/CommuteMorningSettingsRows.kt")
    private val vm = code("ui/page/setting/privacy/PrivacySettingsViewModel.kt")

    @Test fun thePageShowsAllThreeSettings_fromStringResources() {
        listOf("commute_consent_title", "commute_consent_message", "commute_listen_time_title", "commute_listen_time_desc", "commute_autoplay_title", "commute_autoplay_desc")
            .forEach { assertTrue("the rows must use $it", rows.contains("R.string.$it")) }
        assertTrue("the page must show the rows", page.contains("CommuteMorningSettingsRows("))
    }

    @Test fun theRowsAreShownUnconditionally_andWiredToTheRightViewModelCalls() {
        val call = page.substringAfter("CommuteMorningSettingsRows(").substringBefore("onAutoPlay = onToggleAutoPlay")
        assertTrue(
            "the rows must be shown whenever an Activity is available, with no other condition",
            Regex("""\(context as\? Activity\)\?\.let \{ activity ->\s*CommuteMorningSettingsRows\(""").containsMatchIn(page),
        )
        assertTrue(call.contains("onConsent = viewModel::setCommuteScriptGeminiConsent"))
        assertTrue(call.contains("viewModel.setCommuteListenMinuteOfDay(hour * 60 + minute)"))
        assertTrue(call.contains("consent = commuteScriptGeminiConsent"))
        assertTrue(call.contains("listenMinuteOfDay = commuteListenMinuteOfDay"))
        assertTrue(call.contains("autoPlay = commuteAutoPlayOnHeadset"))
        assertTrue(call.contains("permissionDenied = permissionDenied"))
    }

    @Test fun theListenTimeRowReusesTheZenTimePicker() {
        assertTrue(rows.contains("EditionTimeRow("))
    }

    @Test fun theBluetoothPermissionIsRequestedOnlyWhenTheSwitchIsTurnedOn() {
        // Two mentions, both inside the same switch handler: the "already granted?" check and the single request.
        assertEquals("one check and one request", 2, Regex("""BLUETOOTH_CONNECT""").findAll(page).count())
        assertEquals("exactly one place launches the request", 1, Regex("""permissionLauncher\.launch\(""").findAll(page).count())
        val handler = page.substringAfter("val onToggleAutoPlay").substringBefore("BaseScaffold(")
        assertTrue("the request must sit inside the switch handler", handler.contains("permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)"))
        assertFalse("the page must not ask at start", page.substringBefore("val onToggleAutoPlay").contains("permissionLauncher.launch("))
        assertFalse("the rows composable must not ask for permissions itself", rows.contains("BLUETOOTH_CONNECT"))
    }

    @Test fun aDeniedPermissionNeverSavesTheSwitchAsOn() {
        val result = page.substringAfter("ActivityResultContracts.RequestPermission()").substringBefore("val onToggleAutoPlay")
        assertTrue("a denial must be recorded", result.contains("permissionDenied = !granted"))
        assertTrue("only a grant turns the switch on", result.contains("if (granted) viewModel.setCommuteAutoPlayOnHeadset(true)"))
        assertEquals("the callback saves the switch exactly once, and only on a grant", 1, Regex("""setCommuteAutoPlayOnHeadset\(true\)""").findAll(result).count())
    }

    @Test fun savingTheListenTimeReschedulesWithReplace_notKeep() {
        val body = vm.substringAfter("fun setCommuteListenMinuteOfDay(").substringBefore("fun setCommuteAutoPlayOnHeadset(")
        assertTrue(body.contains("ExistingPeriodicWorkPolicy.REPLACE"))
        assertFalse(body.contains("ExistingPeriodicWorkPolicy.KEEP"))
        assertTrue(body.contains("CommuteSchedulePolicy.MINUTES_PER_DAY"))
    }

    @Test fun aSchedulingFailureCannotLoseTheSavedTime() {
        val body = vm.substringAfter("fun setCommuteListenMinuteOfDay(").substringBefore("fun setCommuteAutoPlayOnHeadset(")
        assertTrue(body.indexOf("setCommuteListenMinuteOfDay") < body.indexOf("enqueueDailyWork("))
        assertTrue(body.contains("runCatching") || body.contains("catch (e: Exception)"))
    }

    @Test fun noAdsOrNetworkInTheseSettings() {
        val region = vm.substringAfter("commuteScriptGeminiConsent").take(2500)
        listOf("AdManager", "Rewarded", "OkHttp").forEach { assertFalse(region.contains(it, ignoreCase = true)) }
    }
}
