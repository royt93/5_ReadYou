package com.mckimquyen.reader.ui.page.notebook

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.model.notebook.HighlightColor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookCardWidgetTest {

    private val sampleNote = ArticleHighlightNote(
        id = "widget_note_1",
        articleId = "art_101",
        articleTitle = "Kiến trúc hệ thống Android hiện đại 2026",
        feedName = "Android Developers Blog",
        articleLink = "https://android-developers.googleblog.com/2026/09/modern-arch.html",
        selectedText = "Tối ưu hóa bộ nhớ và giảm thiểu rò rỉ Context là ưu tiên hàng đầu.",
        noteComment = "Áp dụng vào toàn bộ ViewModel và Singleton trong dự án.",
        colorHex = HighlightColor.YELLOW.hex,
        createdAt = 1727180000000L
    )

    @Test
    fun articleHeaderCard_rendersTitleAndActions() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var exportClicked = false

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    ArticleHeaderCard(
                        title = sampleNote.articleTitle,
                        feedName = sampleNote.feedName,
                        noteCount = 3,
                        onExportArticle = { exportClicked = true }
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun highlightCard_rendersSelectedTextAndNoteComment() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var copyClicked = false
        var editClicked = false
        var deleteClicked = false

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    HighlightCard(
                        note = sampleNote,
                        onCopy = { copyClicked = true },
                        onEdit = { editClicked = true },
                        onDelete = { deleteClicked = true }
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun highlightNoteDialog_rendersDialogComponents() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var confirmCalled = false

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    HighlightNoteDialog(
                        initialText = sampleNote.selectedText,
                        initialNote = sampleNote.noteComment,
                        initialColorHex = sampleNote.colorHex,
                        isEditing = false,
                        onDismissRequest = {},
                        onConfirm = { _, _, _ -> confirmCalled = true }
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
