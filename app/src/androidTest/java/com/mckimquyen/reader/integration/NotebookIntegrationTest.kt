package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.MindMapNode
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.model.notebook.HighlightColor
import com.mckimquyen.reader.domain.repository.ArticleHighlightDao
import com.mckimquyen.reader.domain.sv.NotebookExportService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var highlightDao: ArticleHighlightDao
    private lateinit var exportService: NotebookExportService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        highlightDao = database.articleHighlightDao()
        exportService = NotebookExportService()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fullNotebookLifecycle_insertQuerySearchUpdateExportDelete() = runBlocking {
        // 1. Insert highlights across two articles
        val note1 = ArticleHighlightNote(
            id = "int_note_1",
            articleId = "article_ai_2026",
            articleTitle = "AI Second Brain Revolution",
            feedName = "TechCrunch",
            articleLink = "https://techcrunch.com/second-brain-2026",
            selectedText = "Personal knowledge graphs are replacing flat bookmarks in RSS readers.",
            noteComment = "Revolutionary concept for RSS Cat Hub!",
            colorHex = HighlightColor.YELLOW.hex,
            createdAt = 1727181000000L
        )

        val note2 = ArticleHighlightNote(
            id = "int_note_2",
            articleId = "article_ai_2026",
            articleTitle = "AI Second Brain Revolution",
            feedName = "TechCrunch",
            articleLink = "https://techcrunch.com/second-brain-2026",
            selectedText = "On-device processing ensures absolute data privacy.",
            noteComment = "No data sent to external servers without consent.",
            colorHex = HighlightColor.BLUE.hex,
            createdAt = 1727182000000L
        )

        val note3 = ArticleHighlightNote(
            id = "int_note_3",
            articleId = "article_clean_arch",
            articleTitle = "Clean Architecture in Jetpack Compose",
            feedName = "Android Weekly",
            articleLink = "https://androidweekly.net/clean-compose",
            selectedText = "UI state must be immutable and hoisted cleanly.",
            noteComment = "Essential guideline for Compose screens.",
            colorHex = HighlightColor.PINK.hex,
            createdAt = 1727183000000L
        )

        highlightDao.insert(note1)
        highlightDao.insert(note2)
        highlightDao.insert(note3)

        // 2. Query count
        val count = highlightDao.queryCount().first()
        assertEquals(3, count)

        // 3. Query by Article ID
        val article1Notes = highlightDao.queryByArticleId("article_ai_2026").first()
        assertEquals(2, article1Notes.size)
        assertEquals("int_note_1", article1Notes[0].id)
        assertEquals("int_note_2", article1Notes[1].id)

        // 4. Search query
        val searchResults = highlightDao.search("privacy").first()
        assertEquals(1, searchResults.size)
        assertEquals("int_note_2", searchResults[0].id)

        // 5. Update note
        val updatedNote1 = note1.copy(noteComment = "Updated reflection after deeper reading.")
        highlightDao.update(updatedNote1)
        val fetchedUpdated = highlightDao.findById("int_note_1")
        assertNotNull(fetchedUpdated)
        assertEquals("Updated reflection after deeper reading.", fetchedUpdated?.noteComment)

        // 6. Export single article to Markdown with Mermaid MindMap
        val mindMap = ArticleMindMap(
            rootTitle = "Second Brain RSS",
            nodes = listOf(
                MindMapNode(id = "root", label = "Second Brain RSS", depth = 0),
                MindMapNode(id = "b1", label = "Local Highlights", depth = 1, parentId = "root"),
                MindMapNode(id = "b2", label = "Obsidian Sync", depth = 1, parentId = "root", detail = "Direct markdown export")
            )
        )

        val markdown = exportService.exportArticleToMarkdown(
            articleTitle = note1.articleTitle,
            articleLink = note1.articleLink,
            feedName = note1.feedName,
            highlights = listOf(updatedNote1, note2),
            mindMap = mindMap
        )

        assertTrue(markdown.contains("title: \"AI Second Brain Revolution\""))
        assertTrue(markdown.contains("```mermaid"))
        assertTrue(markdown.contains("mindmap"))
        assertTrue(markdown.contains("root((\"Second Brain RSS\"))"))
        assertTrue(markdown.contains("> Personal knowledge graphs are replacing flat bookmarks"))
        assertTrue(markdown.contains("Updated reflection after deeper reading."))

        // 7. Export all notebook
        val allNotes = highlightDao.getAllList()
        val masterMarkdown = exportService.exportAllNotebookToMarkdown(allNotes)
        assertTrue(masterMarkdown.contains("total_highlights: 3"))
        assertTrue(masterMarkdown.contains("total_articles: 2"))
        assertTrue(masterMarkdown.contains("AI Second Brain Revolution"))
        assertTrue(masterMarkdown.contains("Clean Architecture in Jetpack Compose"))

        // 8. Delete note
        highlightDao.deleteById("int_note_3")
        val finalCount = highlightDao.queryCount().first()
        assertEquals(2, finalCount)
        assertNull(highlightDao.findById("int_note_3"))
    }
}
