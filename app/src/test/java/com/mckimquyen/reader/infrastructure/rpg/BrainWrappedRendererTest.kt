package com.mckimquyen.reader.infrastructure.rpg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrainWrappedRendererTest {

    private fun model(topic: String?) = BrainWrappedRenderModel(
        weekLabel = "WEEK 42 · OCT 19–25",
        header = "YOUR WEEK IN KNOWLEDGE",
        weeklyXpLabel = "WEEKLY XP",
        weeklyXp = 240L,
        streakLabel = "CURRENT STREAK",
        streakValue = "6 days",
        topicLabel = "TOPIC THIS WEEK",
        topicValue = topic,
        topicEmptyLabel = "Your topic is taking shape",
        tagline = "Keep your curiosity moving.",
        watermark = "RSS Cat Hub",
    )

    @Test
    fun render_returnsExactStoryDimensionsAndPngEncodes() {
        val bitmap = BrainWrappedRenderer.render(model("Science & Nature"))
        assertEquals(BrainWrappedRenderer.CARD_WIDTH, bitmap.width)
        assertEquals(BrainWrappedRenderer.CARD_HEIGHT, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)

        val bytes = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
        val decoded = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())
        assertEquals(BrainWrappedRenderer.CARD_WIDTH, decoded.width)
        assertEquals(BrainWrappedRenderer.CARD_HEIGHT, decoded.height)
    }

    @Test
    fun render_handlesMissingAndLongTopic() {
        val emptyTopic = BrainWrappedRenderer.render(model(null))
        val longTopic = BrainWrappedRenderer.render(model("Science & Nature ".repeat(20)))
        assertEquals(BrainWrappedRenderer.CARD_WIDTH, emptyTopic.width)
        assertEquals(BrainWrappedRenderer.CARD_WIDTH, longTopic.width)
    }

    @Test
    fun render_usesDarkNeonBackgroundAndDrawsHero() {
        val bitmap = BrainWrappedRenderer.render(model("Science & Nature"))
        assertEquals(BrainWrappedRenderer.NAVY_START, bitmap.getPixel(8, 8))
        assertTrue(bitmap.getPixel(BrainWrappedRenderer.CARD_WIDTH / 2, BrainWrappedRenderer.HERO_SAMPLE_Y) != bitmap.getPixel(8, 8))
    }
}
