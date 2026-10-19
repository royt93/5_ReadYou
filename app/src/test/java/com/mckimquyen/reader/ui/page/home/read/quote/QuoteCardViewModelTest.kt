package com.mckimquyen.reader.ui.page.home.read.quote

import android.content.Intent
import android.graphics.Bitmap
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.quote.QuoteCardSpec
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import com.mckimquyen.reader.infrastructure.quote.QuoteBitmapRenderer
import com.mckimquyen.reader.infrastructure.quote.QuoteCardSharer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuoteCardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val sharer: QuoteCardSharer = mockk()
    private val rendered = mutableListOf<Pair<QuoteCardTemplate, Bitmap>>()
    private var renderBlock: suspend (QuoteCardTemplate) -> Unit = {}

    private val spec = QuoteCardSpec("A short quote.", "Feed", "Author", "Title")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(renderer: QuoteBitmapRenderer = defaultRenderer()) =
        QuoteCardViewModel(sharer, testDispatcher, renderer)

    private fun defaultRenderer() = QuoteBitmapRenderer { _, template ->
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { rendered += template to it }
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(QuoteCardUiState.Idle, viewModel().state.value)
    }

    @Test
    fun start_rendersMinimalistAndEmitsReady() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.start(spec)
        assertEquals(QuoteCardUiState.Rendering, vm.state.value)
        advanceUntilIdle()

        val ready = vm.state.value as QuoteCardUiState.Ready
        assertEquals(QuoteCardTemplate.MINIMALIST, ready.template)
        assertFalse(ready.wasCut)
    }

    @Test
    fun start_withLongQuote_marksWasCut() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.start(spec.copy(quote = "word ".repeat(200)))
        advanceUntilIdle()

        assertTrue((vm.state.value as QuoteCardUiState.Ready).wasCut)
    }

    @Test
    fun selectTemplate_rerendersWithThatTemplate() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.start(spec)
        advanceUntilIdle()

        vm.selectTemplate(QuoteCardTemplate.DARK_STUDIO)
        advanceUntilIdle()

        assertEquals(QuoteCardTemplate.DARK_STUDIO, vm.selectedTemplate.value)
        assertEquals(QuoteCardTemplate.DARK_STUDIO, (vm.state.value as QuoteCardUiState.Ready).template)
        assertEquals(listOf(QuoteCardTemplate.MINIMALIST, QuoteCardTemplate.DARK_STUDIO), rendered.map { it.first })
    }

    @Test
    fun selectTemplate_whileFirstRenderSuspended_cancelsItAndKeepsLatest() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val vm = viewModel(QuoteBitmapRenderer { _, template ->
            // Lần đầu giả lập render chậm: treo tới khi mở cổng.
            if (calls++ == 0) gate.await()
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { rendered += template to it }
        })

        vm.start(spec)
        runCurrent()
        vm.selectTemplate(QuoteCardTemplate.GRADIENT_MATERIAL)
        gate.complete(Unit)
        advanceUntilIdle()

        val ready = vm.state.value as QuoteCardUiState.Ready
        assertEquals(QuoteCardTemplate.GRADIENT_MATERIAL, ready.template)
    }

    @Test
    fun rendererFailure_emitsError() = runTest(testDispatcher) {
        val vm = viewModel(QuoteBitmapRenderer { _, _ -> throw IllegalStateException("boom") })

        vm.start(spec)
        advanceUntilIdle()

        val error = vm.state.value as QuoteCardUiState.Error
        assertEquals(R.string.quote_card_error, error.messageRes)
    }

    @Test
    fun replacingReady_recyclesThePreviousBitmap() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.start(spec)
        advanceUntilIdle()
        val first = (vm.state.value as QuoteCardUiState.Ready).bitmap

        vm.selectTemplate(QuoteCardTemplate.DARK_STUDIO)
        advanceUntilIdle()

        assertTrue("bitmap cũ phải được recycle", first.isRecycled)
        assertFalse((vm.state.value as QuoteCardUiState.Ready).bitmap.isRecycled)
    }

    @Test
    fun dismiss_recyclesBitmapAndReturnsToIdle() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.start(spec)
        advanceUntilIdle()
        val bitmap = (vm.state.value as QuoteCardUiState.Ready).bitmap

        vm.dismiss()

        assertTrue(bitmap.isRecycled)
        assertEquals(QuoteCardUiState.Idle, vm.state.value)
    }

    @Test
    fun prepareShareIntent_writesPngAndBuildsIntent() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.start(spec)
        advanceUntilIdle()
        val file = File("quote.png")
        val intent = Intent(Intent.ACTION_CHOOSER)
        coEvery { sharer.writePng(any(), QuoteCardTemplate.MINIMALIST, any()) } returns file
        every { sharer.buildShareIntent(file, "title") } returns intent

        val result = vm.prepareShareIntent("title")

        assertSame(intent, result)
        coVerify(exactly = 1) { sharer.writePng(any(), QuoteCardTemplate.MINIMALIST, any()) }
        verify(exactly = 1) { sharer.buildShareIntent(file, "title") }
    }

    @Test
    fun prepareShareIntent_whenWriteFails_returnsNullAndEmitsError() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.start(spec)
        advanceUntilIdle()
        coEvery { sharer.writePng(any(), any(), any()) } throws IOException("disk full")

        val result = vm.prepareShareIntent("title")

        assertNull(result)
        assertEquals(R.string.quote_card_error, (vm.state.value as QuoteCardUiState.Error).messageRes)
    }

    @Test
    fun prepareShareIntent_whenNotReady_returnsNullWithoutTouchingSharer() = runTest(testDispatcher) {
        val vm = viewModel()

        val result = vm.prepareShareIntent("title")

        assertNull(result)
        coVerify(exactly = 0) { sharer.writePng(any(), any(), any()) }
    }
}
