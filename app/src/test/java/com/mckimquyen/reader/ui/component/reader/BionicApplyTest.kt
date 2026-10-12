package com.mckimquyen.reader.ui.component.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BionicApplyTest {

    private fun AnnotatedString.boldSpans() =
        spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.substring(it.start, it.end) }

    @Test
    fun disabled_returnsTheSameInstance_soThereIsZeroCost() {
        val input = AnnotatedString("Hello world")
        assertSame(input, input.withBionic(enabled = false))
    }

    @Test
    fun enabled_addsBoldOverTheLeadingLettersOfEachWord() {
        val result = AnnotatedString("Hello world").withBionic(enabled = true)
        assertEquals(listOf("He", "wo"), result.boldSpans())
    }

    @Test
    fun enabled_doesNotChangeTheUnderlyingText_soCopyAndAccessibilityAreIntact() {
        val input = AnnotatedString("Hello,   world!\nSecond line.")
        assertEquals(input.text, input.withBionic(enabled = true).text)
    }

    @Test
    fun enabled_keepsLinkAnnotationsExactlyAsTheyWere() {
        val input = buildAnnotatedString {
            append("Read ")
            pushStringAnnotation("URL", "https://example.test/post")
            append("the full article")
            pop()
            append(" today")
        }

        val result = input.withBionic(enabled = true)

        val before = input.getStringAnnotations("URL", 0, input.length).map { Triple(it.start, it.end, it.item) }
        val after = result.getStringAnnotations("URL", 0, result.length).map { Triple(it.start, it.end, it.item) }
        assertEquals(before, after)
        assertEquals("the full article", result.text.substring(after.single().first, after.single().second))
    }

    @Test
    fun enabled_keepsExistingStyles_andAddsBoldWithoutReplacingThem() {
        val input = buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("emphasised words") }
        }

        val result = input.withBionic(enabled = true)

        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic && it.start == 0 && it.end == input.length })
        // "emphasised" = 10 letters -> 4, "words" = 5 letters -> 2
        assertEquals(listOf("emph", "wo"), result.boldSpans())
    }

    @Test
    fun enabled_onEmptyText_isANoOp() {
        assertEquals(AnnotatedString(""), AnnotatedString("").withBionic(enabled = true))
    }

    @Test
    fun enabled_isIdempotentForTheSameInput() {
        val input = AnnotatedString("Bionic reading helps")
        assertEquals(input.withBionic(true).boldSpans(), input.withBionic(true).boldSpans())
    }
}
