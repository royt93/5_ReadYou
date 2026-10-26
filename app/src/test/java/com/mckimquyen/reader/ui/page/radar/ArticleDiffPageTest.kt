package com.mckimquyen.reader.ui.page.radar

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextDecoration
import com.mckimquyen.reader.domain.radar.DiffPart
import com.mckimquyen.reader.domain.radar.DiffType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArticleDiffPageTest {

    @get:Rule
    val rule = createComposeRule()

    private val edited = ArticleDiff(
        hasHistory = true, removed = false, editedAt = 5L,
        titleDiff = listOf(DiffPart(DiffType.SAME, "Minister"), DiffPart(DiffType.REMOVED, "resigns"), DiffPart(DiffType.ADDED, "stays")),
        bodyDiff = listOf(DiffPart(DiffType.SAME, "He quit on Monday")),
    )

    @Test
    fun diffText_marksRemovedStruckThroughAndAddedDistinct() {
        val text = diffText(edited.titleDiff)

        assertEquals("Minister resigns stays", text.text)
        val struck = text.spanStyles.single { it.item.textDecoration == TextDecoration.LineThrough }
        assertEquals("resigns", text.text.substring(struck.start, struck.end))
        val added = text.spanStyles.single { it.item.textDecoration != TextDecoration.LineThrough }
        assertEquals("stays", text.text.substring(added.start, added.end))
        assertTrue(struck.item.color != added.item.color)
    }

    @Test
    fun readyState_showsHeadlineAndBodyAndNoRemovedNotice() {
        rule.setContent { ArticleDiffContent(ArticleDiffUiState.Ready(edited), onBack = {}) }

        rule.onNodeWithTag(DIFF_PAGE_TITLE_TAG).assertIsDisplayed()
        rule.onNodeWithTag("radar_diff_title_text").assertIsDisplayed()
        rule.onNodeWithTag(DIFF_BODY_TAG).assertIsDisplayed()
        rule.onNodeWithTag(DIFF_REMOVED_NOTICE_TAG).assertDoesNotExist()
    }

    @Test
    fun removedArticle_showsNotice() {
        rule.setContent { ArticleDiffContent(ArticleDiffUiState.Ready(edited.copy(removed = true)), onBack = {}) }
        rule.onNodeWithTag(DIFF_REMOVED_NOTICE_TAG).assertIsDisplayed()
    }

    @Test
    fun noHistory_showsEmptyMessage() {
        rule.setContent {
            ArticleDiffContent(ArticleDiffUiState.Ready(ArticleDiff(false, false, null, emptyList(), emptyList())), onBack = {})
        }
        rule.onNodeWithTag(DIFF_EMPTY_TAG).assertIsDisplayed()
        rule.onNodeWithTag(DIFF_BODY_TAG).assertDoesNotExist()
    }

    @Test
    fun errorState_showsEmptyMessageInsteadOfCrashing() {
        rule.setContent { ArticleDiffContent(ArticleDiffUiState.Error, onBack = {}) }
        rule.onNodeWithTag(DIFF_EMPTY_TAG).assertIsDisplayed()
    }
}
