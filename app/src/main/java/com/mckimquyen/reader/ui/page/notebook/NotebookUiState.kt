package com.mckimquyen.reader.ui.page.notebook

import androidx.annotation.Keep
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.model.notebook.HighlightColor

@Keep
data class NotebookUiState(
    val highlights: List<ArticleHighlightNote> = emptyList(),
    val filteredHighlights: List<ArticleHighlightNote> = emptyList(),
    val selectedColorFilter: HighlightColor? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val exportedMarkdown: String? = null,
    val editingHighlight: ArticleHighlightNote? = null,
)
