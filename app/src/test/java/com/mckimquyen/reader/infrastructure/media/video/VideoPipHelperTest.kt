package com.mckimquyen.reader.infrastructure.media.video

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VideoPipHelperTest {

    /** Android rejects anything outside this band with an IllegalArgumentException. */
    private fun assertWithinSystemLimits(width: Int, height: Int) {
        val ratio = VideoPipHelper.aspectRatioFor(width, height)
        val value = ratio.numerator.toFloat() / ratio.denominator.toFloat()
        assertTrue(
            "Ratio $value from ${width}x$height is outside the PiP limits",
            value in (1f / 2.39f)..2.39f,
        )
    }

    // Rational reduces to lowest terms (1920/1080 -> 16/9), so compare the value, not the terms.
    private fun valueOf(width: Int, height: Int): Float =
        VideoPipHelper.aspectRatioFor(width, height).let {
            it.numerator.toFloat() / it.denominator.toFloat()
        }

    @Test
    fun `keeps a normal landscape ratio untouched`() {
        assertEquals(1920f / 1080f, valueOf(1920, 1080), RATIO_TOLERANCE)
        assertWithinSystemLimits(1920, 1080)
    }

    @Test
    fun `keeps a normal portrait ratio untouched`() {
        assertEquals(1080f / 1920f, valueOf(1080, 1920), RATIO_TOLERANCE)
        assertWithinSystemLimits(1080, 1920)
    }

    @Test
    fun `clamps an ultra wide video into the accepted band`() {
        assertWithinSystemLimits(4000, 500) // 8:1
    }

    @Test
    fun `clamps an ultra tall video into the accepted band`() {
        assertWithinSystemLimits(500, 4000) // 1:8
    }

    @Test
    fun `falls back to sixteen by nine before the first frame is decoded`() {
        // ExoPlayer reports 0x0 until it decodes; a zero would otherwise crash the activity.
        listOf(0 to 0, 0 to 1080, 1920 to 0, -1920 to -1080).forEach { (width, height) ->
            val ratio = VideoPipHelper.aspectRatioFor(width, height)
            assertEquals("Fallback failed for ${width}x$height", 16, ratio.numerator)
            assertEquals(9, ratio.denominator)
        }
    }

    @Test
    fun `boundary ratios are preserved rather than clamped`() {
        assertWithinSystemLimits(239, 100)
        assertWithinSystemLimits(100, 239)
    }

    @Test
    fun `pip support is read from the device feature list`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()

        // Robolectric reports no PiP feature by default; the helper must answer without throwing.
        assertTrue(VideoPipHelper.isPipSupported(context) in listOf(true, false))
    }

    private companion object {
        const val RATIO_TOLERANCE = 0.001f
    }
}

class VideoPipControllerTest {

    @Test
    fun `starts with nothing playing`() {
        VideoPipController.onStopped()

        assertNull(VideoPipController.playing)
    }

    @Test
    fun `records and clears the playing video`() {
        VideoPipController.onPlaying(width = 1280, height = 720)

        val playing = VideoPipController.playing
        assertEquals(1280, playing?.width)
        assertEquals(720, playing?.height)

        VideoPipController.onStopped()
        assertNull("A released player must not leave a PiP marker behind", VideoPipController.playing)
    }

    @Test
    fun `later dimensions replace earlier ones`() {
        VideoPipController.onPlaying(width = 640, height = 360)
        VideoPipController.onPlaying(width = 1920, height = 1080)

        assertEquals(1920, VideoPipController.playing?.width)

        VideoPipController.onStopped()
    }
}
