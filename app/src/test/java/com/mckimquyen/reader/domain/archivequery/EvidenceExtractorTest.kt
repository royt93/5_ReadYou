package com.mckimquyen.reader.domain.archivequery

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EvidenceExtractorTest {

    private fun article(id: String = "a1", title: String = "Title", score: Float = 0.8f) =
        RankedArchiveArticle(
            articleId = id,
            date = Instant.parse("2026-05-01T00:00:00Z"),
            title = title,
            feedName = "TestFeed",
            score = score,
            shortDescription = "",
        )

    // ── HTML to plain text ──

    @Test
    fun `toPlainText strips tags and normalises whitespace`() {
        val html = "<p>Hello   <b>world</b></p>"
        val text = EvidenceExtractor.toPlainText(html)
        assertEquals("Hello world", text)
    }

    @Test
    fun `toPlainText removes script and style content`() {
        val html = "Before<script>alert(1)</script>After<style>.x{}</style>End"
        val text = EvidenceExtractor.toPlainText(html)
        assertTrue(text.contains("Before"))
        assertTrue(text.contains("After"))
        assertTrue(text.contains("End"))
        assertFalse(text.contains("alert"))
        assertFalse(text.contains(".x{"))
    }

    @Test
    fun `toPlainText decodes HTML entities`() {
        val html = "A &amp; B &lt;C&gt; &quot;D&quot; &#39;E&#39;"
        val text = EvidenceExtractor.toPlainText(html)
        assertTrue(text.contains("A & B"))
        assertTrue(text.contains("<C>"))
        assertTrue(text.contains("\"D\""))
        assertTrue(text.contains("'E'"))
    }

    @Test
    fun `toPlainText caps at maxChars preserving surrogate pairs`() {
        // Create text with a supplementary character (emoji 😀 = 2 chars in Java)
        val emoji = "😀" // 😀
        val html = "<p>${"a".repeat(39_998)}${emoji}bbbb</p>"
        val text = EvidenceExtractor.toPlainText(html, maxChars = 40_000)
        assertTrue(text.length <= 40_000)
        // Must not cut in the middle of the surrogate pair
        assertFalse(text.endsWith("\uD83D"))
    }

    @Test
    fun `toPlainText removes noscript elements`() {
        val html = "Visible<noscript>Hidden fallback</noscript>Also visible"
        val text = EvidenceExtractor.toPlainText(html)
        assertFalse(text.contains("Hidden fallback"))
        assertTrue(text.contains("Visible"))
        assertTrue(text.contains("Also visible"))
    }

    // ── blank body ──

    @Test
    fun `extract returns null for blank body`() {
        val result = EvidenceExtractor.extract(article(), "", setOf("topic"))
        assertNull(result)
    }

    @Test
    fun `extract returns null for whitespace-only body`() {
        val result = EvidenceExtractor.extract(article(), "   \n  ", setOf("topic"))
        assertNull(result)
    }

    // ── sentence with topic match ──

    @Test
    fun `extract picks first sentence containing topic token`() {
        val body = "The economy grew. Interest rates rose sharply. Markets reacted."
        val result = EvidenceExtractor.extract(article(), body, setOf("rates"))
        assertNotNull(result)
        assertTrue(result!!.quote.contains("rates"))
        assertTrue(result.topicMatched)
    }

    @Test
    fun `topic token match is whole-word case-insensitive`() {
        val body = "No integration here. Rate of interest changed. Rates went up today."
        val result = EvidenceExtractor.extract(article(), body, setOf("rates"))
        assertNotNull(result)
        assertTrue(result!!.quote.contains("Rates"))
        assertTrue(result.topicMatched)
    }

    // ── fallback to first meaningful sentence ──

    @Test
    fun `extract falls back to first sentence when no topic match`() {
        val body = "The sun rose early. Birds sang loudly. Trees swayed gently."
        val result = EvidenceExtractor.extract(article(), body, setOf("crypto"))
        assertNotNull(result)
        assertTrue(result!!.quote.contains("sun rose early"))
        assertFalse(result.topicMatched)
    }

    @Test
    fun `fallback skips very short sentences`() {
        // Sentence < 20 chars should be skipped in fallback
        val body = "Hi. OK then. The economic outlook remains uncertain for the rest of the year."
        val result = EvidenceExtractor.extract(article(), body, setOf("crypto"))
        assertNotNull(result)
        assertTrue(result!!.quote.contains("economic outlook"))
    }

    // ── quote truncation ──

    @Test
    fun `long sentence is truncated at 300 chars with truncated flag`() {
        val longSentence = "A".repeat(400) + "."
        val result = EvidenceExtractor.extract(article(), longSentence, setOf("A"))
        assertNotNull(result)
        assertTrue(result!!.quote.length <= EvidenceExtractor.MAX_QUOTE_CHARS)
        assertTrue(result.quoteTruncated)
    }

    @Test
    fun `short sentence is not marked truncated`() {
        val body = "Interest rates rose sharply last month."
        val result = EvidenceExtractor.extract(article(), body, setOf("rates"))
        assertNotNull(result)
        assertFalse(result!!.quoteTruncated)
    }

    // ── evidence metadata ──

    @Test
    fun `extract carries article metadata`() {
        val a = article(id = "xyz", title = "My Title")
        val body = "Content about something. Relevant sentence here."
        val result = EvidenceExtractor.extract(a, body, setOf("relevant"))
        assertNotNull(result)
        assertEquals("xyz", result!!.articleId)
        assertEquals("My Title", result.title)
        assertEquals("TestFeed", result.feedName)
    }

    // ── quote is verbatim substring ──

    @Test
    fun `quote is exact substring of normalised body text`() {
        val html = "<p>The <b>Federal Reserve</b> raised interest rates. Markets fell.</p>"
        val plainText = EvidenceExtractor.toPlainText(html)
        val result = EvidenceExtractor.extract(article(), html, setOf("rates"))
        assertNotNull(result)
        assertTrue("Quote must be substring of normalised text",
            plainText.contains(result!!.quote))
        // Whole chosen sentence, not a prefix of it: inline markup must not shorten or reorder the quote.
        assertEquals("The Federal Reserve raised interest rates.", result.quote)
    }

    @Test
    fun `truncated quote is a prefix of the chosen sentence ending at a word boundary`() {
        val sentence = (1..80).joinToString(" ") { "word$it" } + "."
        val result = requireNotNull(EvidenceExtractor.extract(article(), sentence, setOf("word1")))
        assertTrue(result.quoteTruncated)
        assertTrue(sentence.startsWith(result.quote))
        assertTrue(result.quote.length <= EvidenceExtractor.MAX_QUOTE_CHARS)
        assertTrue("must end on a whole word", sentence[result.quote.length] == ' ')
    }
}
