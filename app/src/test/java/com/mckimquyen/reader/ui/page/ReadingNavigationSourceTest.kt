package com.mckimquyen.reader.ui.page

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compose code that cannot run in a plain JVM test is pinned by its source: the stale capture in LaunchedEffect(Unit) reloaded the article on every return to the reading
 * screen, "next article" ignored the read-position setting, and RSVP kept running after Back or a tap outside.
 */
class ReadingNavigationSourceTest {
    private val root: File = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }
    private val page = File(root, "ui/page/home/read/ReadingPage.kt").readText()
    private val rsvp = File(root, "ui/page/rsvp/RsvpReaderDialog.kt").readText()

    @Test
    fun returningToTheReadingScreenComparesAgainstTheCurrentArticleNotTheFirstComposition() {
        val effect = page.substringAfter("currentBackStackEntryFlow.collect").substringBefore("LaunchedEffect(readingUiState.articleWithFeed?.article?.id) {")
        assertTrue("the id check must use rememberUpdatedState", "rememberUpdatedState(readingUiState.articleWithFeed?.article?.id)" in page)
        assertTrue("the collector must compare with the updated id", "currentArticleIdForNav != articleId" in effect)
        assertTrue("the stale readingUiState must not be read inside the collector", "readingUiState.articleWithFeed?.article?.id != articleId" !in effect)
    }

    @Test
    fun nextArticleHonoursTheReadPositionSyncSetting() {
        assertTrue(
            "initData(readingUiState.nextArticleId, autoTts, readPositionSyncEnabled)" in page,
        )
    }

    @Test
    fun rsvpPausesOnEveryWayOfClosingTheDialog() {
        val dismiss = rsvp.substringAfter("Dialog(").substringBefore("DialogEdgeToEdge()")
        assertTrue("onDismissRequest must pause playback", "viewModel.pause()" in dismiss && "onDismissRequest" in dismiss)
    }
}
