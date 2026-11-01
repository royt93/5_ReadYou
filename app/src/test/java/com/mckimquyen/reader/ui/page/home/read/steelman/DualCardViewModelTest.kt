package com.mckimquyen.reader.ui.page.home.read.steelman

import android.content.Intent
import android.graphics.Bitmap
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.infrastructure.quote.DualBitmapRenderer
import com.mckimquyen.reader.infrastructure.quote.QuoteCardSharer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class DualCardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val sharer: QuoteCardSharer = mockk()
    private val spec = DualPerspectiveSpec("Top", "A", "d1", "Bottom", "B", "d2", "warn")

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun renderer() = DualBitmapRenderer { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
    private fun vm(r: DualBitmapRenderer = renderer()) = DualCardViewModel(sharer, testDispatcher, r)

    @Test fun initialState_isIdle() { assertEquals(DualCardUiState.Idle, vm().state.value) }

    @Test fun start_rendersAndEmitsReady() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        assertEquals(DualCardUiState.Rendering, vm.state.value)
        advanceUntilIdle()
        assertFalse((vm.state.value as DualCardUiState.Ready).wasCut)
    }

    @Test fun start_withLongHeadline_marksWasCut() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec.copy(topTitle = "word ".repeat(200)))
        advanceUntilIdle()
        assertTrue((vm.state.value as DualCardUiState.Ready).wasCut)
    }

    @Test fun rendererFailure_emitsError() = runTest(testDispatcher) {
        val vm = vm(DualBitmapRenderer { throw IllegalStateException("boom") })
        vm.start(spec)
        advanceUntilIdle()
        assertEquals(R.string.echo_dual_error, (vm.state.value as DualCardUiState.Error).messageRes)
    }

    @Test fun dismiss_recyclesBitmapAndReturnsToIdle() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        val bitmap = (vm.state.value as DualCardUiState.Ready).bitmap
        vm.dismiss()
        assertTrue(bitmap.isRecycled)
        assertEquals(DualCardUiState.Idle, vm.state.value)
    }

    @Test fun startingAgain_recyclesThePreviousBitmap() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        val first = (vm.state.value as DualCardUiState.Ready).bitmap
        vm.start(spec.copy(bottomTitle = "Other"))
        advanceUntilIdle()
        assertTrue(first.isRecycled)
        assertFalse((vm.state.value as DualCardUiState.Ready).bitmap.isRecycled)
    }

    @Test fun prepareShareIntent_writesPngWithDualPrefixAndShareText() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        val file = File("dual.png")
        val intent = Intent(Intent.ACTION_CHOOSER)
        coEvery { sharer.writePng(any(), "dual", any()) } returns file
        every { sharer.buildShareIntent(file, "title", "#RSSCatHub #Debate") } returns intent

        val result = vm.prepareShareIntent("title", "#RSSCatHub #Debate")

        assertSame(intent, result)
        coVerify(exactly = 1) { sharer.writePng(any(), "dual", any()) }
    }

    @Test fun prepareShareIntent_whenWriteFails_returnsNullAndEmitsError() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        coEvery { sharer.writePng(any(), any<String>(), any()) } throws IOException("disk full")
        assertNull(vm.prepareShareIntent("title", "x"))
        assertEquals(R.string.echo_dual_error, (vm.state.value as DualCardUiState.Error).messageRes)
    }

    @Test fun prepareShareIntent_whenNotReady_doesNotTouchSharer() = runTest(testDispatcher) {
        assertNull(vm().prepareShareIntent("title", "x"))
        coVerify(exactly = 0) { sharer.writePng(any(), any<String>(), any()) }
    }
}
