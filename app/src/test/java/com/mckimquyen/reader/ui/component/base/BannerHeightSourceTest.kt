package com.mckimquyen.reader.ui.component.base

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Feeds "All" card used `.height(88.dp)`, so Arabic text (taller lines) was cut off at the bottom on the Pixel (2026-11-25). Robolectric does not use the device
 * font, so a layout test cannot reproduce it; this guards the cause instead: the height of the banner must stay a minimum, never a fixed value.
 */
class BannerHeightSourceTest {
    private val src = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
        .resolve("java/com/mckimquyen/reader/ui/component/base/Banner.kt").readText()

    @Test fun theBannerHeightIsAMinimumNotAFixedValue() {
        assertTrue("Banner must use heightIn(min = ...)", "heightIn(min =" in src)
        assertTrue("Banner must not set a fixed height", Regex("""\.height\(""").findAll(src).none())
    }

    @Test fun theTextColumnDoesNotFillAFixedHeight() {
        assertTrue("fillMaxHeight pins the text column to the old fixed height", "fillMaxHeight" !in src)
    }
}
