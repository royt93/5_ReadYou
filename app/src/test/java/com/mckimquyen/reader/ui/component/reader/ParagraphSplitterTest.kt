package com.mckimquyen.reader.ui.component.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphSplitterTest {

    @Test
    fun aSingleParagraph_isReturnedAsTheSameInstance() {
        val text = AnnotatedString("Just one paragraph.")
        val parts = splitParagraphs(text)
        assertEquals(1, parts.size)
        assertSame(text, parts.single())
    }

    @Test
    fun blankLinesSeparateParagraphs_andTheSeparatorIsDropped() {
        val parts = splitParagraphs(AnnotatedString("First one.\n\nSecond one.\n\nThird one."))
        assertEquals(listOf("First one.", "Second one.", "Third one."), parts.map { it.text })
    }

    @Test
    fun threeOrMoreNewlines_actAsOneSeparator_andNoEmptyPartsAreProduced() {
        val parts = splitParagraphs(AnnotatedString("\n\nFirst.\n\n\n\nSecond.\n\n"))
        assertEquals(listOf("First.", "Second."), parts.map { it.text })
    }

    @Test
    fun aSingleNewline_staysInsideItsParagraph() {
        val parts = splitParagraphs(AnnotatedString("Line one\nline two\n\nNext"))
        assertEquals(listOf("Line one\nline two", "Next"), parts.map { it.text })
    }

    @Test
    fun emptyAndBlankInput_yieldNoUsablePart() {
        assertTrue(splitParagraphs(AnnotatedString("")).all { it.text.isEmpty() })
        assertTrue(splitParagraphs(AnnotatedString("\n\n")).isEmpty())
    }

    @Test
    fun spanStylesFollowTheirParagraph_withRebasedOffsets() {
        val source = buildAnnotatedString {
            append("Alpha beta.\n\nGamma delta.")
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 13, 18) // "Gamma"
        }
        val parts = splitParagraphs(source)

        assertTrue(parts[0].spanStyles.isEmpty())
        val style = parts[1].spanStyles.single()
        assertEquals("Gamma", parts[1].text.substring(style.start, style.end))
    }

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun linkAnnotationsFollowTheirParagraph() {
        val source = buildAnnotatedString {
            append("Intro text.\n\nSee the story here.")
            addStringAnnotation("URL", "https://example.test/post", 17, 26) // "the story"
        }
        val parts = splitParagraphs(source)

        assertTrue(parts[0].getStringAnnotations("URL", 0, parts[0].length).isEmpty())
        val link = parts[1].getStringAnnotations("URL", 0, parts[1].length).single()
        assertEquals("https://example.test/post", link.item)
        assertEquals("the story", parts[1].text.substring(link.start, link.end))
    }

    @Test
    fun aStyleSpanningTheSeparator_isClippedToEachParagraph() {
        val source = buildAnnotatedString {
            append("One.\n\nTwo.")
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, 10)
        }
        val parts = splitParagraphs(source)
        assertEquals(listOf("One.", "Two."), parts.map { it.text })
        parts.forEach { part ->
            val style = part.spanStyles.single()
            assertEquals(0, style.start)
            assertEquals(part.length, style.end)
        }
    }
}
