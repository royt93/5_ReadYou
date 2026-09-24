package com.mckimquyen.reader.ui.page.notebook

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.model.notebook.HighlightColor
import com.mckimquyen.reader.domain.repository.ArticleHighlightDao
import com.mckimquyen.reader.domain.sv.NotebookExportService
import com.mckimquyen.reader.infrastructure.ai.ArticleMindMapExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val highlightDao: ArticleHighlightDao,
    private val exportService: NotebookExportService,
) : ViewModel() {

    companion object {
        private const val TAG = "roy93~Notebook"
    }

    internal var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    private val _uiState = MutableStateFlow(NotebookUiState(isLoading = true))
    val uiState: StateFlow<NotebookUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "NotebookViewModel initialized, observing highlights from Room")
        observeHighlights()
    }

    internal fun observeHighlights() {
        viewModelScope.launch(ioDispatcher) {
            highlightDao.queryAll().collect { list ->
                Log.d(TAG, "Received ${list.size} highlights from Room")
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        val filtered = applyFilter(list, current.searchQuery, current.selectedColorFilter)
                        current.copy(
                            highlights = list,
                            filteredHighlights = filtered,
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.highlights, query, current.selectedColorFilter)
            current.copy(searchQuery = query, filteredHighlights = filtered)
        }
    }

    fun setColorFilter(color: HighlightColor?) {
        _uiState.update { current ->
            val nextColor = if (current.selectedColorFilter == color) null else color
            val filtered = applyFilter(current.highlights, current.searchQuery, nextColor)
            current.copy(selectedColorFilter = nextColor, filteredHighlights = filtered)
        }
    }

    private fun applyFilter(
        list: List<ArticleHighlightNote>,
        query: String,
        color: HighlightColor?,
    ): List<ArticleHighlightNote> {
        return list.filter { item ->
            val matchQuery = if (query.isBlank()) {
                true
            } else {
                item.selectedText.contains(query, ignoreCase = true) ||
                        item.noteComment.contains(query, ignoreCase = true) ||
                        item.articleTitle.contains(query, ignoreCase = true) ||
                        item.feedName.contains(query, ignoreCase = true)
            }
            val matchColor = if (color == null) {
                true
            } else {
                item.colorHex.equals(color.hex, ignoreCase = true)
            }
            matchQuery && matchColor
        }
    }

    fun addHighlight(
        articleId: String,
        articleTitle: String,
        feedName: String = "",
        articleLink: String = "",
        selectedText: String,
        noteComment: String = "",
        colorHex: String = HighlightColor.YELLOW.hex,
        onDone: (() -> Unit)? = null,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val note = ArticleHighlightNote(
                articleId = articleId,
                articleTitle = articleTitle,
                feedName = feedName,
                articleLink = articleLink,
                selectedText = selectedText.trim(),
                noteComment = noteComment.trim(),
                colorHex = colorHex,
                createdAt = System.currentTimeMillis(),
            )
            Log.d(TAG, "Saving highlight to Room: id=${note.id}, articleId=$articleId, color=$colorHex")
            highlightDao.insert(note)
            withContext(Dispatchers.Main) {
                onDone?.invoke()
            }
        }
    }

    fun updateHighlight(highlight: ArticleHighlightNote, onDone: (() -> Unit)? = null) {
        viewModelScope.launch(ioDispatcher) {
            Log.d(TAG, "Updating highlight: id=${highlight.id}")
            highlightDao.update(highlight)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(editingHighlight = null) }
                onDone?.invoke()
            }
        }
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch(ioDispatcher) {
            Log.d(TAG, "Deleting highlight: id=$id")
            highlightDao.deleteById(id)
        }
    }

    fun setEditingHighlight(highlight: ArticleHighlightNote?) {
        _uiState.update { it.copy(editingHighlight = highlight) }
    }

    fun exportSingleArticle(
        articleId: String,
        articleTitle: String,
        articleLink: String = "",
        feedName: String = "",
        articleHtmlContent: String = "",
        onExported: (String) -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            Log.d(TAG, "Exporting single article to Markdown: id=$articleId, title=$articleTitle")
            val highlights = highlightDao.getListByArticleId(articleId)
            val mindMap = if (articleHtmlContent.isNotBlank()) {
                val plainText = articleHtmlContent.replace("<[^>]*>".toRegex(), " ")
                ArticleMindMapExtractor.extractOfflineMindMap(articleTitle, plainText)
            } else null

            val md = exportService.exportArticleToMarkdown(
                articleTitle = articleTitle,
                articleLink = articleLink.takeIf { it.isNotBlank() },
                feedName = feedName.takeIf { it.isNotBlank() },
                highlights = highlights,
                mindMap = mindMap,
            )

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(exportedMarkdown = md) }
                onExported(md)
            }
        }
    }

    fun exportAll(onExported: (String) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            Log.d(TAG, "Exporting all notebook highlights to master Markdown")
            val highlights = highlightDao.getAllList()
            val md = exportService.exportAllNotebookToMarkdown(highlights)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(exportedMarkdown = md) }
                onExported(md)
            }
        }
    }

    fun clearExportedMarkdown() {
        _uiState.update { it.copy(exportedMarkdown = null) }
    }
}
