package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import com.mckimquyen.reader.domain.model.quote.QuoteCardSpec
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
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
class QuoteCardRendererTest {

    private val spec = QuoteCardSpec(
        quote = "The best way to predict the future is to invent it.",
        feedName = "BBC News",
        author = "Alan Kay",
        articleTitle = "On inventing the future",
    )

    private fun render(template: QuoteCardTemplate, s: QuoteCardSpec = spec): Bitmap =
        QuoteCardRenderer.render(s, template)

    /** Đếm pixel trong vùng khác màu nền tham chiếu (lấy tại góc trên-trái của vùng). */
    private fun countNonBackground(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val background = bitmap.getPixel(x0, y0)
        var count = 0
        for (x in x0..x1) for (y in y0..y1) if (bitmap.getPixel(x, y) != background) count++
        return count
    }

    @Test
    fun render_returnsExactSizeForEveryTemplate() {
        QuoteCardTemplate.entries.forEach { template ->
            val bitmap = render(template)
            assertEquals(template.name, QuoteCardRenderer.CARD_WIDTH, bitmap.width)
            assertEquals(template.name, QuoteCardRenderer.CARD_HEIGHT, bitmap.height)
        }
    }

    @Test
    fun render_backgroundsDifferBetweenTemplates() {
        val pixels = QuoteCardTemplate.entries.map { render(it).getPixel(8, 8) }
        assertEquals("ba template phải có màu nền khác nhau", pixels.size, pixels.toSet().size)
    }

    @Test
    fun render_gradientChangesTopToBottom() {
        val bitmap = render(QuoteCardTemplate.GRADIENT_MATERIAL)
        assertNotEquals(bitmap.getPixel(8, 8), bitmap.getPixel(8, QuoteCardRenderer.CARD_HEIGHT - 8))
    }

    @Test
    fun render_drawsQuoteTextPixels() {
        QuoteCardTemplate.entries.forEach { template ->
            val drawn = countNonBackground(render(template), 90, 300, 990, 900)
            assertTrue("${template.name}: phải có pixel chữ, nhận $drawn", drawn > 0)
        }
    }

    @Test
    fun render_drawsFooterPixels() {
        QuoteCardTemplate.entries.forEach { template ->
            val drawn = countNonBackground(render(template), 90, 1180, 990, 1330)
            assertTrue("${template.name}: footer phải có pixel, nhận $drawn", drawn > 0)
        }
    }

    @Test
    fun render_longQuoteStaysInsideMargins() {
        val long = spec.copy(quote = "word ".repeat(1000))
        QuoteCardTemplate.entries.forEach { template ->
            val bitmap = render(template, long)
            for (y in 300..900) {
                val edge = bitmap.getPixel(5, y)
                for (x in 0 until 40) {
                    assertEquals("${template.name}: lề trái bị vẽ tràn tại ($x,$y)", edge, bitmap.getPixel(x, y))
                }
            }
        }
    }

    @Test
    fun render_handlesArabicChineseAndEmoji() {
        listOf("مرحبا بالعالم", "你好，世界", "Hello 😀").forEach { text ->
            val bitmap = render(QuoteCardTemplate.MINIMALIST, spec.copy(quote = text))
            val drawn = countNonBackground(bitmap, 90, 300, 990, 900)
            assertTrue("'$text' phải vẽ được chữ, nhận $drawn", drawn > 0)
        }
    }

    @Test
    fun render_authorChangesTheFooter() {
        val withAuthor = render(QuoteCardTemplate.MINIMALIST)
        val withoutAuthor = render(QuoteCardTemplate.MINIMALIST, spec.copy(author = null))
        var different = 0
        for (x in 90..990 step 3) for (y in 1180..1330 step 3) {
            if (withAuthor.getPixel(x, y) != withoutAuthor.getPixel(x, y)) different++
        }
        assertTrue("footer phải khác khi bỏ tác giả, nhận $different", different > 0)
        assertTrue(countNonBackground(withoutAuthor, 90, 1180, 990, 1330) > 0)
    }
}
