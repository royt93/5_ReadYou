package com.mckimquyen.reader.ui.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * On the Pixel the Settings page measured #FDEDEB while Privacy, Local sync, Archive Q&A, Feed Autopilot and Brain RPG measured #FFFBFF: 23 pages used the tinted
 * `surface onLight inverseOnSurface`, the others plain `surface` or the default. Every page opened from Settings now takes its background from settingsPageContainerColor().
 */
class SettingsPageBackgroundTest {
    private val src: File = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }

    private fun read(rel: String) = File(src, rel).readText()

    private val settingsFlow = listOf(
        "ui/page/setting/SettingsPage.kt",
        "ui/page/setting/privacy/PrivacySettingsPage.kt",
        "ui/page/setting/ai/AiSettingsPage.kt",
        "ui/page/p2psync/LocalSyncPage.kt",
        "ui/page/archivequery/ArchiveQueryPage.kt",
        "ui/page/autopilot/FeedAutopilotPage.kt",
        "ui/page/rpg/BrainRpgPage.kt",
        "ui/page/about/AboutActivity.kt",
        "ui/page/setting/vip/VipManagementPage.kt",
        "ui/page/secondbrain/SecondBrainPage.kt",
        "ui/page/notebook/NotebookPage.kt",
    )

    @Test
    fun everySettingsFlowPageUsesTheSharedBackgroundToken() {
        val off = settingsFlow.filter { "containerColor = settingsPageContainerColor()" !in read(it) }
        assertEquals("pages that pick their own background: $off", emptyList<String>(), off)
    }

    @Test
    fun noPageRepeatsTheTintedLiteralAnyMore() {
        val literal = "surface onLight MaterialTheme.colorScheme.inverseOnSurface"
        val offenders = src.walkTopDown().filter { it.extension == "kt" && it.name != "BaseScaffold.kt" && literal in it.readText() }.map { it.name }.toList()
        assertTrue("the tint belongs in settingsPageContainerColor() only: $offenders", offenders.isEmpty())
    }

    @Test
    fun theTokenIsDefinedOnceAndKeepsTheLightTint() {
        val scaffold = read("ui/component/base/BaseScaffold.kt")
        assertEquals(1, Regex("""fun settingsPageContainerColor\(\)""").findAll(scaffold).count())
        assertTrue("MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface" in scaffold)
    }

    @Test
    fun dailyDigestPageHasABackButtonAndTheSharedBackground() {
        val page = read("ui/page/digest/DigestPage.kt")
        assertTrue("DigestPage must sit in BaseScaffold", "BaseScaffold(" in page)
        assertTrue("DigestPage must take its background from the shared token", "containerColor = settingsPageContainerColor()" in page)
        assertTrue("DigestPage must expose onBack", "onBack: (() -> Unit)? = null" in page)
        val route = read("ui/page/common/HomeEntry.kt").substringAfter("route = RouteName.DAILY_DIGEST").substringBefore("route = \"\${RouteName.READING}")
        assertTrue("the DAILY_DIGEST route must pass a real back action", "onBack = { navController.popBackStack() }" in route)
    }
}
