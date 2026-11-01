package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualCardRendererTest {
    private val spec = DualPerspectiveSpec(
        topTitle = "Musk says SpaceX restricted Ukraine's Starlink connection",
        topSource = "BBC News", topDate = "25 Oct 2026",
        bottomTitle = "Scientists warn Starlink constellation disrupts research",
        bottomSource = "Science Wire", bottomDate = "1 Nov 2026", cue = "warn",
    )

    private fun nonBackground(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val background = bitmap.getPixel(x0, y0)
        var count = 0
        for (x in x0..x1) for (y in y0..y1) if (bitmap.getPixel(x, y) != background) count++
        return count
    }

    @Test fun render_hasExactNineBySixteenSize() {
        val bitmap = DualCardRenderer.render(spec)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test fun halves_haveDifferentBackgrounds() {
        val bitmap = DualCardRenderer.render(spec)
        assertNotEquals(bitmap.getPixel(8, 8), bitmap.getPixel(8, DualCardRenderer.CARD_HEIGHT - 8))
    }

    @Test fun bothHalves_drawHeadlinePixels() {
        val bitmap = DualCardRenderer.render(spec)
        val half = DualCardRenderer.CARD_HEIGHT / 2
        assertTrue(nonBackground(bitmap, 90, 200, 990, half - 200) > 0)
        assertTrue(nonBackground(bitmap, 90, half + 200, 990, DualCardRenderer.CARD_HEIGHT - 300) > 0)
    }

    @Test fun footer_drawsDisclaimerPixels() {
        val bitmap = DualCardRenderer.render(spec)
        val y1 = DualCardRenderer.CARD_HEIGHT - 20
        assertTrue(nonBackground(bitmap, 90, y1 - 120, 990, y1) > 0)
    }

    @Test fun veryLongHeadlinesAndEmptyFields_doNotCrash() {
        val long = spec.copy(topTitle = "word ".repeat(300), bottomTitle = "word ".repeat(300))
        assertEquals(1920, DualCardRenderer.render(long).height)
        val empty = DualPerspectiveSpec("", "", "", "", "", "", "")
        assertEquals(1080, DualCardRenderer.render(empty).width)
    }

    @Test fun unicodeHeadlines_doNotCrash() {
        val unicode = spec.copy(topTitle = "تحذير 警告 ⚠️ Starlink", bottomTitle = "Cảnh báo về Starlink 🚀")
        assertEquals(1080, DualCardRenderer.render(unicode).width)
    }

    @Test fun longHeadline_staysInsideSideMargins() {
        val bitmap = DualCardRenderer.render(spec.copy(topTitle = "word ".repeat(300)))
        for (y in 200..800) {
            val edge = bitmap.getPixel(5, y)
            for (x in 0 until 40) assertEquals("lề trái bị vẽ tràn tại ($x,$y)", edge, bitmap.getPixel(x, y))
        }
    }
}
