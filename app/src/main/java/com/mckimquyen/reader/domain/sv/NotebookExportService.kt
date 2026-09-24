package com.mckimquyen.reader.domain.sv

import android.util.Log
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.model.notebook.HighlightColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to export article highlights, personal notes, and concept mind maps
 * into clean, Obsidian/Notion/Logseq-compatible Markdown files.
 */
@Singleton
class NotebookExportService @Inject constructor() {

    companion object {
        private const val TAG = "roy93~Notebook"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Exports highlights and optional MindMap of a single article to Markdown.
     */
    fun exportArticleToMarkdown(
        articleTitle: String,
        articleLink: String? = null,
        feedName: String? = null,
        highlights: List<ArticleHighlightNote>,
        mindMap: ArticleMindMap? = null,
    ): String {
        Log.d(TAG, "[exportArticleToMarkdown] title=\"$articleTitle\", highlightsCount=${highlights.size}, hasMindMap=${mindMap != null}")
        val currentDate = dateFormat.format(Date())

        return buildString {
            // Obsidian / Notion YAML Frontmatter
            appendLine("---")
            appendLine("title: \"${escapeYaml(articleTitle)}\"")
            if (!feedName.isNullOrBlank()) appendLine("source: \"${escapeYaml(feedName)}\"")
            if (!articleLink.isNullOrBlank()) appendLine("url: \"$articleLink\"")
            appendLine("exported: \"$currentDate\"")
            appendLine("tags:")
            appendLine("  - rss-cat-hub")
            appendLine("  - second-brain")
            appendLine("  - highlights")
            appendLine("---")
            appendLine()

            // Header
            appendLine("# $articleTitle")
            if (!feedName.isNullOrBlank()) {
                appendLine("*Source: $feedName*")
            }
            if (!articleLink.isNullOrBlank()) {
                appendLine("[Read Original Article]($articleLink)")
            }
            appendLine()

            // Highlights Section
            if (highlights.isNotEmpty()) {
                appendLine("## 📝 Highlights & Personal Notes")
                appendLine()
                highlights.forEachIndexed { index, item ->
                    val color = HighlightColor.fromHex(item.colorHex)
                    val colorBadge = when (color) {
                        HighlightColor.YELLOW -> "🟡 [Yellow]"
                        HighlightColor.BLUE -> "🔵 [Blue]"
                        HighlightColor.PINK -> "🔴 [Pink]"
                        HighlightColor.GREEN -> "🟢 [Green]"
                        HighlightColor.ORANGE -> "🟠 [Orange]"
                    }
                    appendLine("### ${index + 1}. $colorBadge")
                    // Blockquote for quotation
                    item.selectedText.lines().forEach { line ->
                        appendLine("> $line")
                    }
                    appendLine()
                    if (item.noteComment.isNotBlank()) {
                        appendLine("**💡 Reflection / Note:**")
                        appendLine(item.noteComment)
                        appendLine()
                    }
                }
            }

            // Embedded Mermaid Mind Map
            if (mindMap != null && mindMap.nodes.isNotEmpty()) {
                appendLine("## 🧠 Concept Mind Map")
                appendLine()
                appendLine("```mermaid")
                appendLine("mindmap")
                val rootLabel = cleanMermaid(mindMap.rootTitle.ifBlank { articleTitle })
                appendLine("  root((\"$rootLabel\"))")

                val rootNode = mindMap.getRootNode()
                val topLevelBranches = if (rootNode != null) {
                    mindMap.findChildren(rootNode.id)
                } else {
                    mindMap.nodes.filter { it.depth == 1 }
                }

                topLevelBranches.forEach { branch ->
                    appendLine("    [\"${cleanMermaid(branch.label)}\"]")
                    mindMap.findChildren(branch.id).forEach { sub ->
                        appendLine("      (\"${cleanMermaid(sub.label)}\")")
                        if (sub.detail.isNotBlank()) {
                            appendLine("        ::icon(fa fa-book)")
                        }
                    }
                }
                appendLine("```")
                appendLine()
            }
        }.trim()
    }

    /**
     * Exports all notebook highlights from multiple articles into a master Markdown file.
     */
    fun exportAllNotebookToMarkdown(
        highlights: List<ArticleHighlightNote>,
    ): String {
        Log.d(TAG, "[exportAllNotebookToMarkdown] totalHighlights=${highlights.size}")
        val currentDate = dateFormat.format(Date())
        val groupedByArticle = highlights.groupBy { it.articleId }

        return buildString {
            appendLine("---")
            appendLine("title: \"RSS Cat Hub — Master Knowledge Notebook\"")
            appendLine("exported: \"$currentDate\"")
            appendLine("total_highlights: ${highlights.size}")
            appendLine("total_articles: ${groupedByArticle.size}")
            appendLine("tags:")
            appendLine("  - rss-cat-hub")
            appendLine("  - second-brain")
            appendLine("  - master-notebook")
            appendLine("---")
            appendLine()
            appendLine("# 📚 Knowledge Notebook (Second Brain)")
            appendLine("*Exported on $currentDate • Total ${highlights.size} highlights across ${groupedByArticle.size} articles*")
            appendLine()

            groupedByArticle.forEach { (_, notes) ->
                val firstNote = notes.first()
                appendLine("---")
                appendLine()
                appendLine("## ${firstNote.articleTitle.ifBlank { "Untitled Article" }}")
                if (firstNote.feedName.isNotBlank()) {
                    appendLine("*Source: ${firstNote.feedName}*")
                }
                if (firstNote.articleLink.isNotBlank()) {
                    appendLine("[Original Article Link](${firstNote.articleLink})")
                }
                appendLine()

                notes.forEachIndexed { idx, note ->
                    val color = HighlightColor.fromHex(note.colorHex)
                    val colorBadge = when (color) {
                        HighlightColor.YELLOW -> "🟡"
                        HighlightColor.BLUE -> "🔵"
                        HighlightColor.PINK -> "🔴"
                        HighlightColor.GREEN -> "🟢"
                        HighlightColor.ORANGE -> "🟠"
                    }
                    appendLine("#### $colorBadge Quote #${idx + 1}")
                    note.selectedText.lines().forEach { line ->
                        appendLine("> $line")
                    }
                    appendLine()
                    if (note.noteComment.isNotBlank()) {
                        appendLine("**💡 Note:** ${note.noteComment}")
                        appendLine()
                    }
                }
            }
        }.trim()
    }

    private fun escapeYaml(value: String): String {
        return value.replace("\"", "\\\"").replace("\n", " ")
    }

    private fun cleanMermaid(value: String): String {
        return value.replace("\"", "'")
            .replace("\n", " ")
            .take(50)
            .trim()
    }
}
