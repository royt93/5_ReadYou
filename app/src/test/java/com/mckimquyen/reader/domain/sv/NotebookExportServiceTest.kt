package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.MindMapNode
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.model.notebook.HighlightColor
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotebookExportServiceTest {

    private lateinit var exportService: NotebookExportService

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        exportService = NotebookExportService()
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun exportArticleToMarkdown_withHighlightsAndMindMap_generatesValidMarkdown() {
        val highlights = listOf(
            ArticleHighlightNote(
                id = "n-1",
                articleId = "art-1",
                articleTitle = "AI Revolution in 2026",
                feedName = "Tech Insights",
                articleLink = "https://tech.example.com/ai-2026",
                selectedText = "On-device AI is now capable of real-time reasoning without cloud dependencies.",
                noteComment = "Critical point for mobile app architecture.",
                colorHex = HighlightColor.YELLOW.hex
            ),
            ArticleHighlightNote(
                id = "n-2",
                articleId = "art-1",
                articleTitle = "AI Revolution in 2026",
                feedName = "Tech Insights",
                articleLink = "https://tech.example.com/ai-2026",
                selectedText = "Privacy-first architecture eliminates data leakage risks.",
                noteComment = "Complies with strict data regulations.",
                colorHex = HighlightColor.BLUE.hex
            )
        )

        val mindMap = ArticleMindMap(
            rootTitle = "AI Revolution",
            nodes = listOf(
                MindMapNode(id = "root", label = "AI Revolution", depth = 0),
                MindMapNode(id = "b1", label = "On-Device Processing", depth = 1, parentId = "root"),
                MindMapNode(id = "b2", label = "Privacy Vault", depth = 1, parentId = "root", detail = "No server communication")
            )
        )

        val md = exportService.exportArticleToMarkdown(
            articleTitle = "AI Revolution in 2026",
            articleLink = "https://tech.example.com/ai-2026",
            feedName = "Tech Insights",
            highlights = highlights,
            mindMap = mindMap
        )

        // Verify YAML frontmatter
        assertTrue(md.startsWith("---"))
        assertTrue(md.contains("title: \"AI Revolution in 2026\""))
        assertTrue(md.contains("source: \"Tech Insights\""))
        assertTrue(md.contains("url: \"https://tech.example.com/ai-2026\""))
        assertTrue(md.contains("tags:"))
        assertTrue(md.contains("  - rss-cat-hub"))

        // Verify highlights section
        assertTrue(md.contains("## 📝 Highlights & Personal Notes"))
        assertTrue(md.contains("> On-device AI is now capable of real-time reasoning"))
        assertTrue(md.contains("**💡 Reflection / Note:**"))
        assertTrue(md.contains("Critical point for mobile app architecture."))
        assertTrue(md.contains("> Privacy-first architecture eliminates data leakage risks."))

        // Verify Mermaid block
        assertTrue(md.contains("```mermaid"))
        assertTrue(md.contains("mindmap"))
        assertTrue(md.contains("root((\"AI Revolution\"))"))
        assertTrue(md.contains("[\"On-Device Processing\"]"))
        assertTrue(md.contains("[\"Privacy Vault\"]"))
    }

    @Test
    fun exportArticleToMarkdown_emptyHighlightsAndNoMindMap_generatesHeaderOnly() {
        val md = exportService.exportArticleToMarkdown(
            articleTitle = "Simple Article",
            articleLink = null,
            feedName = null,
            highlights = emptyList(),
            mindMap = null
        )

        assertTrue(md.contains("title: \"Simple Article\""))
        assertTrue(md.contains("# Simple Article"))
        assertFalse(md.contains("## 📝 Highlights & Personal Notes"))
        assertFalse(md.contains("```mermaid"))
    }

    @Test
    fun exportAllNotebookToMarkdown_multipleArticles_groupsProperly() {
        val highlights = listOf(
            ArticleHighlightNote(
                id = "n-1",
                articleId = "art-1",
                articleTitle = "Article 1",
                feedName = "Feed A",
                selectedText = "Quote from article 1",
                noteComment = "Note 1"
            ),
            ArticleHighlightNote(
                id = "n-2",
                articleId = "art-2",
                articleTitle = "Article 2",
                feedName = "Feed B",
                selectedText = "Quote from article 2",
                noteComment = "Note 2"
            )
        )

        val md = exportService.exportAllNotebookToMarkdown(highlights)

        assertTrue(md.contains("total_highlights: 2"))
        assertTrue(md.contains("total_articles: 2"))
        assertTrue(md.contains("## Article 1"))
        assertTrue(md.contains("> Quote from article 1"))
        assertTrue(md.contains("## Article 2"))
        assertTrue(md.contains("> Quote from article 2"))
    }

    @Test
    fun exportArticleToMarkdown_withQuotesAndSpecialChars_escapesCorrectly() {
        val highlights = listOf(
            ArticleHighlightNote(
                id = "n-special",
                articleId = "art-special",
                articleTitle = "Quotes \"And\" Details\nSecond Line",
                selectedText = "Text with \"double quotes\" and \nmultiple lines",
                noteComment = "Note with \"quotes\""
            )
        )

        val md = exportService.exportArticleToMarkdown(
            articleTitle = "Quotes \"And\" Details\nSecond Line",
            highlights = highlights
        )

        assertTrue(md.contains("title: \"Quotes \\\"And\\\" Details Second Line\""))
        assertTrue(md.contains("> Text with \"double quotes\" and "))
    }
}
