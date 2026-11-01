package com.mckimquyen.reader.domain.model.bounty

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Renders a [Dossier] as Markdown for export (BOUNTY-02). Pure and deterministic. The text leaves the app, so the
 * labels are English and not localized. All article-derived text is escaped and flattened to one line so it can
 * neither change Markdown structure nor emit raw HTML.
 */
object DossierMarkdownBuilder {

    const val LIMITATION_LINE = "Built only from articles saved in this app. It may omit viewpoints and is not a fact-check."
    const val NONE_SUMMARY = "_No sentence in your saved articles contains this phrase._"
    const val NONE_TIMELINE = "_No articles._"
    const val NONE_PHRASES = "_No phrase recurs in at least 2 articles._"
    const val ONE_SOURCE = "_Your archive has only one source on this topic._"

    private const val PHRASES_NOTE =
        "Capitalized phrases that recur across articles. This counts text, it is not named-entity recognition."

    private val LINE_BREAKS = Regex("[\\r\\n]+")
    private val LEADING_ORDERED_LIST = Regex("^(\\d+)([.)])")
    private val ALWAYS_ESCAPED = setOf('\\', '`', '*', '_', '[', ']', '#', '<', '>', '|')
    private val LEADING_ESCAPED = setOf('-', '+')

    fun build(dossier: Dossier, zone: ZoneId = ZoneId.systemDefault()): String {
        fun day(date: Date): LocalDate = Instant.ofEpochMilli(date.time).atZone(zone).toLocalDate()
        val out = StringBuilder()
        fun line(text: String = "") = out.append(text).append('\n')

        line("# Dossier: ${escape(dossier.query)}")
        line()
        line("Generated ${Instant.ofEpochMilli(dossier.generatedAtMillis).atZone(zone).toLocalDate()}")
        line()
        line(LIMITATION_LINE)
        line()

        line("## Summary")
        line()
        if (dossier.summary.isEmpty()) line(NONE_SUMMARY)
        dossier.summary.forEach { quote ->
            line("> ${escape(quote.text)}")
            line(">")
            line("> — ${escape(quote.feedName)}, ${day(quote.date)}")
            line()
        }
        if (dossier.summary.isNotEmpty()) line()

        line("## Timeline")
        line()
        if (dossier.timeline.isEmpty()) line(NONE_TIMELINE)
        dossier.timeline.forEach { entry ->
            line("### ${entry.day}")
            line()
            entry.articles.forEach { line("- ${escape(it.title)} (${escape(it.feedName)})") }
            line()
        }
        if (dossier.timeline.isNotEmpty()) line()

        line("## Recurring phrases")
        line()
        line(PHRASES_NOTE)
        line()
        if (dossier.phrases.isEmpty()) line(NONE_PHRASES)
        dossier.phrases.forEach { line("- ${escape(it.phrase)} (${it.articleCount} articles)") }
        line()

        line("## Sources")
        line()
        if (dossier.framings.isEmpty()) line(ONE_SOURCE)
        dossier.framings.forEach { framing ->
            line("### ${escape(framing.feedName)}")
            line()
            framing.headlines.forEach { line("- ${escape(it.title)}") }
            line()
        }

        return out.toString().trimEnd() + "\n"
    }

    /** Flattens line breaks, backslash-escapes Markdown/HTML control characters, and guards leading block markers. */
    private fun escape(raw: String): String {
        val flat = raw.replace(LINE_BREAKS, " ").trim()
        val body = StringBuilder()
        flat.forEach { c ->
            if (c in ALWAYS_ESCAPED) body.append('\\')
            body.append(c)
        }
        var text = body.toString()
        if (text.isNotEmpty() && text[0] in LEADING_ESCAPED) text = "\\$text"
        return LEADING_ORDERED_LIST.replace(text) { "${it.groupValues[1]}\\${it.groupValues[2]}" }
    }
}
