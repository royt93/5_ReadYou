package com.mckimquyen.reader.ui.page

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compose and Intent code that a plain JVM test cannot run is pinned by its source.
 * - The Reels gesture-exclusion band sat on the activity's single view and was never removed, so it swallowed Back on every other screen after Reels was closed.
 * - HomeEntry wiped the intent extras before the effect that read EXTRA_START_COMMUTE, so the "Morning Radio ready" notification never opened the player.
 */
class NavigationFixesSourceTest {
    private val root: File = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }
    private val reels = File(root, "ui/page/reels/ReelsPage.kt").readText()
    private val home = File(root, "ui/page/common/HomeEntry.kt").readText()

    @Test
    fun reelsRemovesItsGestureExclusionWhenItLeavesTheScreen() {
        val modifier = reels.substringAfter("private fun Modifier.leftEdgeGestureExclusion()").substringBefore("The pager itself")
        assertTrue("an onDispose must exist", "onDispose" in modifier)
        assertTrue("onDispose must clear the rects", "systemGestureExclusionRects = emptyList()" in modifier)
    }

    @Test
    fun theCommuteFlagIsReadBeforeTheExtrasAreWiped() {
        val read = home.indexOf("EXTRA_START_COMMUTE")
        val wipe = home.indexOf("intent?.replaceExtras(null)")
        assertTrue("both must exist", read >= 0 && wipe >= 0)
        assertTrue("the extra must be read before replaceExtras(null)", read < wipe)
        assertTrue("the effect must use the saved flag, not the wiped intent", "if (startCommute)" in home)
    }

    @Test
    fun theStartDestinationIsChosenOnceNotOnEveryRecomposition() {
        // Seen on a Pixel: the first article opened after a fresh install jumped back to Feeds, because the start destination flipped STARTUP -> FEEDS when the flag turned
        // false and NavHost reset its back stack.
        val nav = home.substringAfter("NavHost(").substringBefore("animatedComposable(route = RouteName.STARTUP")
        assertTrue("startDestination must come from a remembered value", "startDestination = startDestination" in nav)
        assertTrue("the value must be remembered", "val startDestination = remember {" in home)
        assertTrue("isFirstLaunch must not be read inside the NavHost call", "context.isFirstLaunch" !in nav)
    }
}
