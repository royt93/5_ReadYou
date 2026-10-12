package com.mckimquyen.reader.ui.page.secondbrain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.secondbrain.BrainArticleInfo
import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import com.mckimquyen.reader.domain.sv.SecondBrainSv
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class SecondBrainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sv = mockk<SecondBrainSv>()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val graph = BrainGraph(
        nodes = listOf(
            BrainNode("ai", "Ai", 1, listOf("a1"), 100L, 200L),
            BrainNode("web", "Web", 1, listOf("a2"), 100L, 200L),
        ),
        edges = listOf(BrainEdge("ai", "web", 1)),
        minTime = 100L,
        maxTime = 200L,
        articleInfo = mapOf("a1" to BrainArticleInfo("a1", "One", 100L), "a2" to BrainArticleInfo("a2", "Two", 200L)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SecondBrainViewModel(context, sv)

    @Test
    fun startsLoading_thenShowsTheGraph() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        assertEquals(SecondBrainUiState.Loading, vm.state.value)

        advanceUntilIdle()

        val ready = vm.state.value as SecondBrainUiState.Ready
        assertSame(graph.nodes, ready.graph.nodes)
        assertEquals(100L to 200L, ready.from to ready.to)
    }

    @Test
    fun noArticles_showsEmpty() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns BrainGraph(emptyList(), emptyList(), 0L, 0L)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(SecondBrainUiState.Empty, vm.state.value)
    }

    @Test
    fun aFailingLoad_showsError_insteadOfCrashing() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } throws IllegalStateException("db closed")
        val vm = vm()
        advanceUntilIdle()
        assertEquals(SecondBrainUiState.Error, vm.state.value)
    }

    @Test
    fun refreshAfterAnError_recovers() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } throws IllegalStateException("boom") andThen graph
        val vm = vm()
        advanceUntilIdle()
        assertEquals(SecondBrainUiState.Error, vm.state.value)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value is SecondBrainUiState.Ready)
    }

    @Test
    fun selectNode_keepsAKnownNode_andIgnoresAnUnknownOne() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        advanceUntilIdle()

        vm.selectNode("ai")
        assertEquals("ai", (vm.state.value as SecondBrainUiState.Ready).selectedNodeId)

        vm.selectNode("does-not-exist")
        assertEquals(null, (vm.state.value as SecondBrainUiState.Ready).selectedNodeId)

        vm.selectNode(null)
        assertEquals(null, (vm.state.value as SecondBrainUiState.Ready).selectedNodeId)
    }

    @Test
    fun selectNode_beforeTheGraphIsReady_isANoOp() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        vm.selectNode("ai") // still Loading
        assertEquals(SecondBrainUiState.Loading, vm.state.value)
    }

    @Test
    fun setRange_reloadsWithTheWindow_butTheSliderBoundsStayThoseOfTheFullData() = runTest(dispatcher) {
        coEvery { sv.load(any(), Long.MIN_VALUE, Long.MAX_VALUE) } returns graph
        val narrowed = graph.copy(minTime = 150L, maxTime = 160L) // what the Sv returns for the window
        coEvery { sv.load(any(), 150L, 160L) } returns narrowed
        val vm = vm()
        advanceUntilIdle()

        vm.selectNode("ai")
        vm.setRange(150L, 160L)
        advanceUntilIdle()

        val ready = vm.state.value as SecondBrainUiState.Ready
        assertEquals("slider keeps the full-data bounds", 100L to 200L, ready.graph.minTime to ready.graph.maxTime)
        assertEquals(150L to 160L, ready.from to ready.to)
        assertEquals("selection survives a window change", "ai", ready.selectedNodeId)
        coVerify { sv.load(any(), 150L, 160L) }
    }

    @Test
    fun setRange_beforeReady_isANoOp() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        vm.setRange(1L, 2L)
        advanceUntilIdle()
        coVerify(exactly = 0) { sv.load(any(), 1L, 2L) }
    }

    @Test
    fun aNewerReload_cancelsTheOlderOneSoAStaleAnswerNeverWins() = runTest(dispatcher) {
        val slow = CompletableDeferred<BrainGraph>()
        var calls = 0
        coEvery { sv.load(any(), any(), any()) } coAnswers {
            if (++calls == 1) slow.await() else graph
        }
        val vm = vm()
        advanceUntilIdle() // first load is suspended on `slow`
        assertEquals(SecondBrainUiState.Loading, vm.state.value)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value is SecondBrainUiState.Ready)

        slow.complete(BrainGraph(emptyList(), emptyList(), 0L, 0L)) // the stale (empty) answer arrives late
        advanceUntilIdle()
        assertTrue("stale answer must not overwrite the newer one", vm.state.value is SecondBrainUiState.Ready)
    }
}
