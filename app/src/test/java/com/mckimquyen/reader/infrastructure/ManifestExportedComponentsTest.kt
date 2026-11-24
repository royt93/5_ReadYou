package com.mckimquyen.reader.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every component the release exports must be one the app means to expose, and each must either carry an intent filter or be listed here with the reason.
 * An exported Activity without a filter that anyone can start was the finding of the pre-release audit (androidx.activity.ComponentActivity, added for the UI test
 * suite). The test dependency androidx.compose.ui:ui-test-manifest adds it to the DEBUG manifest on its own, so the main manifest must not carry it.
 */
class ManifestExportedComponentsTest {
    private val manifest: String = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.exists() }.readText()

    private data class Component(val tag: String, val name: String, val exported: Boolean, val hasIntentFilter: Boolean)

    private fun components(): List<Component> =
        Regex("""<(activity|service|receiver|provider)\b(.*?)(/>|</\1>)""", RegexOption.DOT_MATCHES_ALL).findAll(manifest).map { m ->
            val body = m.groupValues[2]
            Component(
                tag = m.groupValues[1],
                name = Regex("""android:name="([^"]+)"""").find(body)?.groupValues?.get(1) ?: "?",
                exported = Regex("""android:exported="true"""").containsMatchIn(body),
                hasIntentFilter = "<intent-filter" in body,
            )
        }.toList()

    @Test
    fun noBareComponentActivityIsExported() {
        val bare = components().filter { it.name == "androidx.activity.ComponentActivity" }
        assertEquals("the UI test Activity belongs to the debug manifest (ui-test-manifest adds it), not to the release", emptyList<Component>(), bare)
    }

    @Test
    fun everyExportedActivityHasAnIntentFilter() {
        // An exported Activity with no intent filter can be started by any app on the phone. SplashActivity, MainActivity, ReadNewsActivity and
        // ShareTargetActivity carry filters (launcher, deep link, share); AboutActivity is opened by SettingsPage with an explicit intent inside the app, so it is not exported.
        val offenders = components().filter { it.tag == "activity" && it.exported && !it.hasIntentFilter }.map { it.name }
        assertEquals("exported activities that anyone can start without a filter: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun theAboutScreenIsOnlyOpenedFromInsideTheApp() {
        val about = components().single { it.name == ".ui.page.about.AboutActivity" }
        assertEquals(false, about.exported)
    }

    @Test
    fun theExportedComponentsAreTheKnownOnes() {
        val exported = components().filter { it.exported }.map { it.name }.toSet()
        val expected = setOf(
            ".infrastructure.android.SplashActivity", ".infrastructure.android.MainActivity",
            ".ui.page.readnews.ReadNewsActivity", ".ui.page.share.ShareTargetActivity",
            ".infrastructure.audio.CommuteMediaSessionService", ".ui.widget.ArticleWidgetProvider",
        )
        assertEquals("a new or removed exported component must be a conscious decision: update this list after reviewing it", expected, exported)
        assertTrue(exported.isNotEmpty())
    }
}
