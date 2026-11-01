package com.mckimquyen.reader.domain.model.bounty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class DossierMarkdownBuilderTest {

    private val zone = ZoneId.of("UTC")
    private val generatedAt = 1_700_000_000_000L // 2023-11-14 UTC

    private fun ref(id: String, title: String, feed: String = "BBC News", date: Long = generatedAt) =
        DossierArticleRef(id, title, feed, Date(date))

    private fun dossier(
        query: String = "DeepSeek",
        summary: List<QuotedSentence> = emptyList(),
        timeline: List<TimelineEntry> = emptyList(),
        phrases: List<RecurringPhrase> = emptyList(),
        framings: List<SourceFraming> = emptyList(),
        sourceCount: Int = 2,
    ) = Dossier(query, summary, timeline, phrases, framings, sourceCount, generatedAt)

    private fun render(d: Dossier) = DossierMarkdownBuilder.build(d, zone)

    @Test
    fun header_containsQueryGenerationDateAndLimitation() {
        val md = render(dossier())

        assertTrue(md.lines().first().startsWith("# "))
        assertTrue(md.lines().first().contains("DeepSeek"))
        assertTrue(md.contains("2023-11-14"))
        assertTrue(md.contains(DossierMarkdownBuilder.LIMITATION_LINE))
    }

    @Test
    fun sections_appearInOrder() {
        val md = render(dossier())
        val positions = listOf("## Summary", "## Timeline", "## Recurring phrases", "## Sources").map { md.indexOf(it) }

        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun summary_rendersQuoteWithFeedAndDate() {
        val md = render(
            dossier(summary = listOf(QuotedSentence("DeepSeek launches model.", "Wired", Date(generatedAt), "1"))),
        )

        assertTrue(md.lines().any { it == "> DeepSeek launches model." })
        assertTrue(md.lines().any { it.startsWith("> ") && it.contains("Wired") && it.contains("2023-11-14") })
    }

    @Test
    fun timeline_listsDaysWithArticles() {
        val md = render(
            dossier(
                timeline = listOf(
                    TimelineEntry(LocalDate.parse("2023-11-14"), listOf(ref("1", "First story"), ref("2", "Second story", "Wired"))),
                ),
            ),
        )

        assertTrue(md.contains("### 2023-11-14"))
        assertTrue(md.contains("- First story (BBC News)"))
        assertTrue(md.contains("- Second story (Wired)"))
    }

    @Test
    fun phrases_showArticleCountAndHonestLabel() {
        val md = render(dossier(phrases = listOf(RecurringPhrase("Sam Altman", 3))))

        assertTrue(md.contains("- Sam Altman (3 articles)"))
        assertTrue(md.contains("not named-entity recognition"))
    }

    @Test
    fun sources_groupHeadlinesPerFeed() {
        val md = render(
            dossier(
                framings = listOf(
                    SourceFraming("BBC News", listOf(ref("1", "BBC take"))),
                    SourceFraming("Wired", listOf(ref("2", "Wired take", "Wired"))),
                ),
            ),
        )

        assertTrue(md.contains("### BBC News"))
        assertTrue(md.contains("- BBC take"))
        assertTrue(md.contains("### Wired"))
    }

    @Test
    fun emptySections_renderExplicitNoneLines_notBlankHeadings() {
        val md = render(dossier(sourceCount = 1))

        assertTrue(md.contains(DossierMarkdownBuilder.NONE_SUMMARY))
        assertTrue(md.contains(DossierMarkdownBuilder.NONE_PHRASES))
        assertTrue(md.contains(DossierMarkdownBuilder.ONE_SOURCE))
        // every "## " heading is followed by a non-blank line
        val lines = md.lines()
        lines.forEachIndexed { i, line ->
            if (line.startsWith("## ")) assertTrue(lines.getOrNull(i + 1).orEmpty().isNotBlank() || lines.getOrNull(i + 2).orEmpty().isNotBlank())
        }
    }

    @Test
    fun controlCharacters_areEscaped() {
        val hostile = "*bold* _it_ [link](http://x) `code` # h <b>x</b> | a"
        val md = render(dossier(timeline = listOf(TimelineEntry(LocalDate.parse("2023-11-14"), listOf(ref("1", hostile))))))
        val line = md.lines().first { it.startsWith("- ") && it.contains("bold") }

        assertTrue(line.contains("\\*bold\\*"))
        assertTrue(line.contains("\\_it\\_"))
        assertTrue(line.contains("\\[link\\]"))
        assertTrue(line.contains("\\`code\\`"))
        assertTrue(line.contains("\\<b\\>"))
        assertTrue(line.contains("\\|"))
        assertFalse(line.contains("<b>"))
    }

    @Test
    fun leadingBlockMarkers_inQuotesAreEscaped() {
        val quotes = listOf("# Heading", "- item", "> nested", "1. numbered", "+ plus")
            .mapIndexed { i, t -> QuotedSentence(t, "F", Date(generatedAt), "$i") }
        val md = render(dossier(summary = quotes))
        val quoteLines = md.lines().filter { it.startsWith("> ") && !it.contains("—") }

        assertEquals(listOf("> \\# Heading", "> \\- item", "> \\> nested", "> 1\\. numbered", "> \\+ plus"), quoteLines)
    }

    @Test
    fun newlineInTitle_cannotStartNewBlock() {
        val md = render(
            dossier(timeline = listOf(TimelineEntry(LocalDate.parse("2023-11-14"), listOf(ref("1", "Line one\n# Injected\r\n- bullet"))))),
        )

        assertFalse(md.lines().any { it.startsWith("# Injected") })
        assertFalse(md.lines().any { it == "- bullet" })
        assertTrue(md.lines().any { it.startsWith("- Line one ") && it.contains("Injected") })
    }

    @Test
    fun hostileQueryAndFeedName_cannotBreakHeadings() {
        val md = render(
            dossier(
                query = "x\n## Fake",
                framings = listOf(SourceFraming("Feed\n## Evil", listOf(ref("1", "t"))), SourceFraming("B", listOf(ref("2", "u")))),
            ),
        )

        assertFalse(md.lines().any { it.startsWith("## Fake") || it.startsWith("## Evil") })
    }

    @Test
    fun output_isDeterministic_andNeverContainsRawHtml() {
        val d = dossier(summary = listOf(QuotedSentence("<script>alert(1)</script>", "F", Date(generatedAt), "1")))

        assertEquals(render(d), render(d))
        assertFalse(render(d).contains("<script>"))
    }

    @Test
    fun date_followsInjectedZone() {
        val auckland = DossierMarkdownBuilder.build(dossier(), ZoneId.of("Pacific/Auckland"))

        assertTrue(auckland.contains("2023-11-15"))
    }
}
