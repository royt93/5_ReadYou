package com.mckimquyen.reader.ui.component.reader

import androidx.compose.ui.text.AnnotatedString

private val PARAGRAPH_BREAK = Regex("\n{2,}")

/**
 * The reader composes consecutive blocks into one [AnnotatedString] separated by blank lines. This splits it
 * back into paragraphs so each can be translated on its own. Styles and link annotations follow their
 * paragraph (clipped and rebased by [AnnotatedString.subSequence]). Text without a blank line comes back as
 * the same instance; blank-only input yields no parts.
 */
fun splitParagraphs(text: AnnotatedString): List<AnnotatedString> {
    val source = text.text
    if (!source.contains("\n\n")) return listOf(text)

    val parts = ArrayList<AnnotatedString>()
    var start = 0
    for (match in PARAGRAPH_BREAK.findAll(source)) {
        if (match.range.first > start) parts += text.subSequence(start, match.range.first)
        start = match.range.last + 1
    }
    if (start < source.length) parts += text.subSequence(start, source.length)
    return parts.filter { it.text.isNotBlank() }
}
