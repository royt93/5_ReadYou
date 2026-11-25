package com.mckimquyen.reader.ui.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * - Triage "Read now" and Reels opened the reading screen without launchSingleTop, so a double tap stacked two copies and one Back seemed to do nothing.
 * - ShareTargetActivity read EXTRA_TEXT with getStringExtra, which is null for a Spanned, so sharing from such an app said "invalid URL".
 */
class SmallNavigationFixesSourceTest {
    private val root: File = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }

    private fun readingNavigations(rel: String) =
        File(root, rel).readLines().filter { "navigate(\"\${RouteName.READING}/" in it }

    @Test
    fun everyReadingNavigationInTriageAndReelsIsSingleTop() {
        val lines = readingNavigations("ui/page/triage/TriagePage.kt") + readingNavigations("ui/page/reels/ReelsPage.kt")
        assertEquals("expected the 2 Triage and 1 Reels call sites", 3, lines.size)
        val loose = lines.filter { "launchSingleTop = true" !in it }
        assertTrue("navigations without launchSingleTop: $loose", loose.isEmpty())
    }

    @Test
    fun theShareTargetReadsTheTextAsACharSequence() {
        val src = File(root, "ui/page/share/ShareTargetActivity.kt").readText()
        assertTrue("getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()" in src)
        assertTrue("getStringExtra(Intent.EXTRA_TEXT)" !in src)
    }

    @Test
    fun theFlowSearchFlagSurvivesLeavingForAnArticle() {
        // remember was lost on the trip to an article; on return the flag was false and the effect cleared the query the user had typed.
        val flow = File(root, "ui/page/home/flow/FlowPage.kt").readText()
        assertTrue("onSearch must be rememberSaveable", "var onSearch by rememberSaveable" in flow)
        assertTrue("and start from the stored query, not from false", "homeUiState.searchContent.isNotBlank()" in flow.substringAfter("var onSearch by rememberSaveable").substringBefore("\n"))
    }
}
