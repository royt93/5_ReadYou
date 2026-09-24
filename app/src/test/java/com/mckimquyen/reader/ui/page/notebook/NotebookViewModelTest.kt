package com.mckimquyen.reader.ui.page.notebook

import com.mckimquyen.reader.domain.model.notebook.ArticleHighlightNote
import com.mckimquyen.reader.domain.model.notebook.HighlightColor
import com.mckimquyen.reader.domain.repository.ArticleHighlightDao
import com.mckimquyen.reader.domain.sv.NotebookExportService
import com.mckimquyen.reader.infrastructure.ai.ArticleMindMapExtractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val highlightDao = mockk<ArticleHighlightDao>(relaxed = true)
    private val exportService = NotebookExportService()

    private val sampleNotes = listOf(
        ArticleHighlightNote(
            id = "note-1",
            articleId = "art-1",
            articleTitle = "Compose Best Practices",
            feedName = "Android Dev",
            selectedText = "State hoisting decouples presentation from logic.",
            noteComment = "Remember to follow this in UI layers.",
            colorHex = HighlightColor.YELLOW.hex
        ),
        ArticleHighlightNote(
            id = "note-2",
            articleId = "art-2",
            articleTitle = "Kotlin Coroutines in Depth",
            feedName = "Kotlin Blog",
            selectedText = "Structured concurrency prevents leakages.",
            noteComment = "Use viewModelScope appropriately.",
            colorHex = HighlightColor.BLUE.hex
        ),
        ArticleHighlightNote(
            id = "note-3",
            articleId = "art-1",
            articleTitle = "Compose Best Practices",
            feedName = "Android Dev",
            selectedText = "Avoid passing ViewModel to deeply nested composables.",
            noteComment = "",
            colorHex = HighlightColor.PINK.hex
        )
    )

    @Before
    fun setUp() {
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.d(any(), any()) } returns 0
        io.mockk.every { android.util.Log.i(any(), any()) } returns 0
        io.mockk.every { android.util.Log.w(any(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.e(any(), any<String>()) } returns 0
        Dispatchers.setMain(testDispatcher)
        coEvery { highlightDao.queryAll() } returns flowOf(sampleNotes)
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NotebookViewModel {
        val vm = NotebookViewModel(highlightDao, exportService)
        vm.ioDispatcher = testDispatcher
        vm.observeHighlights()
        return vm
    }

    @Test
    fun observeHighlights_loadsAllNotesIntoState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.highlights.size)
        assertEquals(3, state.filteredHighlights.size)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun setSearchQuery_filtersByMatchingTextOrNote() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("coroutines")
        val state1 = viewModel.uiState.value
        assertEquals(1, state1.filteredHighlights.size)
        assertEquals("note-2", state1.filteredHighlights[0].id)

        // Case-insensitive query matching note comment
        viewModel.setSearchQuery("HOISTING")
        val state2 = viewModel.uiState.value
        assertEquals(1, state2.filteredHighlights.size)
        assertEquals("note-1", state2.filteredHighlights[0].id)

        // Clear query restores all
        viewModel.setSearchQuery("")
        val state3 = viewModel.uiState.value
        assertEquals(3, state3.filteredHighlights.size)
    }

    @Test
    fun setColorFilter_filtersByColorHex() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setColorFilter(HighlightColor.BLUE)
        val state = viewModel.uiState.value
        assertEquals(1, state.filteredHighlights.size)
        assertEquals(HighlightColor.BLUE, state.selectedColorFilter)
        assertEquals("note-2", state.filteredHighlights[0].id)

        // Clicking same color toggles it off
        viewModel.setColorFilter(HighlightColor.BLUE)
        val stateToggled = viewModel.uiState.value
        assertNull(stateToggled.selectedColorFilter)
        assertEquals(3, stateToggled.filteredHighlights.size)
    }

    @Test
    fun addHighlight_insertsIntoDao() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        var callbackFired = false
        viewModel.addHighlight(
            articleId = "art-new",
            articleTitle = "New Article",
            selectedText = "New memorable excerpt",
            noteComment = "Insightful thought",
            colorHex = HighlightColor.GREEN.hex
        ) {
            callbackFired = true
        }
        advanceUntilIdle()

        coVerify(exactly = 1) {
            highlightDao.insert(match {
                it.articleId == "art-new" &&
                        it.selectedText == "New memorable excerpt" &&
                        it.colorHex == HighlightColor.GREEN.hex
            })
        }
        assertEquals(true, callbackFired)
    }

    @Test
    fun updateHighlight_updatesDaoAndClearsEditing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val target = sampleNotes[0]
        viewModel.setEditingHighlight(target)
        assertEquals(target, viewModel.uiState.value.editingHighlight)

        val updated = target.copy(noteComment = "Revised note")
        viewModel.updateHighlight(updated)
        advanceUntilIdle()

        coVerify(exactly = 1) { highlightDao.update(updated) }
        assertNull(viewModel.uiState.value.editingHighlight)
    }

    @Test
    fun deleteHighlight_callsDaoDeleteById() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteHighlight("note-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { highlightDao.deleteById("note-1") }
    }

    @Test
    fun exportAll_triggersExportServiceAndCallback() = runTest(testDispatcher) {
        coEvery { highlightDao.getAllList() } returns sampleNotes
        val viewModel = createViewModel()
        advanceUntilIdle()

        var exportedContent: String? = null
        viewModel.exportAll { markdown ->
            exportedContent = markdown
        }
        advanceUntilIdle()

        assertEquals(true, exportedContent != null)
        assertEquals(true, exportedContent?.contains("Compose Best Practices"))
        assertEquals(exportedContent, viewModel.uiState.value.exportedMarkdown)
    }
}
