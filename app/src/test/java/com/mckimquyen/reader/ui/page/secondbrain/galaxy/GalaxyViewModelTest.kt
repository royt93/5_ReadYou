package com.mckimquyen.reader.ui.page.secondbrain.galaxy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraph
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLine
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import com.mckimquyen.reader.domain.sv.GalaxySv
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
import org.junit.Assert.assertNull
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
class GalaxyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sv = mockk<GalaxySv>()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val graph = GalaxyGraph(
        stars = listOf(
            GalaxyStar("a1", "One", "BBC", 100L, setOf("ai")),
            GalaxyStar("a2", "Two", "BBC", 200L, setOf("ai")),
        ),
        lines = listOf(GalaxyLine("a1", "a2", 1)),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = GalaxyViewModel(context, sv, dispatcher)

    @Test
    fun startsLoading_thenShowsTheGalaxy() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        assertEquals(GalaxyUiState.Loading, vm.state.value)

        advanceUntilIdle()

        val ready = vm.state.value as GalaxyUiState.Ready
        assertSame(graph, ready.graph)
        assertNull(ready.selectedStarId)
        assertEquals(Long.MIN_VALUE to Long.MAX_VALUE, ready.from to ready.to)
    }

    @Test
    fun noReadArticles_showsEmpty() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns GalaxyGraph.EMPTY
        val vm = vm()
        advanceUntilIdle()
        assertEquals(GalaxyUiState.Empty, vm.state.value)
    }

    @Test
    fun aFailingLoad_showsError_andRefreshRecovers() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } throws IllegalStateException("db closed") andThen graph
        val vm = vm()
        advanceUntilIdle()
        assertEquals(GalaxyUiState.Error, vm.state.value)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value is GalaxyUiState.Ready)
    }

    @Test
    fun selectStar_keepsAKnownStar_andIgnoresAnUnknownOne() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        advanceUntilIdle()

        vm.selectStar("a2")
        assertEquals("a2", (vm.state.value as GalaxyUiState.Ready).selectedStarId)

        vm.selectStar("ghost")
        assertNull((vm.state.value as GalaxyUiState.Ready).selectedStarId)

        vm.selectStar("a1")
        vm.selectStar(null)
        assertNull((vm.state.value as GalaxyUiState.Ready).selectedStarId)
    }

    @Test
    fun selectStar_beforeTheGalaxyIsReady_isANoOp() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        vm.selectStar("a1")
        assertEquals(GalaxyUiState.Loading, vm.state.value)
    }

    @Test
    fun setRange_reloadsWithTheWindow_andDropsASelectionThatLeftIt() = runTest(dispatcher) {
        val narrowed = GalaxyGraph(listOf(graph.stars[1]), emptyList())
        coEvery { sv.load(any(), Long.MIN_VALUE, Long.MAX_VALUE) } returns graph
        coEvery { sv.load(any(), 150L, 250L) } returns narrowed
        val vm = vm()
        advanceUntilIdle()
        vm.selectStar("a1")

        vm.setRange(150L, 250L)
        advanceUntilIdle()

        val ready = vm.state.value as GalaxyUiState.Ready
        assertEquals(150L to 250L, ready.from to ready.to)
        assertEquals(listOf("a2"), ready.graph.stars.map { it.id })
        assertNull("a1 is outside the window", ready.selectedStarId)
        coVerify { sv.load(any(), 150L, 250L) }
    }

    @Test
    fun setRange_keepsASelectionThatIsStillInTheWindow() = runTest(dispatcher) {
        coEvery { sv.load(any(), any(), any()) } returns graph
        val vm = vm()
        advanceUntilIdle()
        vm.selectStar("a2")

        vm.setRange(0L, 300L)
        advanceUntilIdle()

        assertEquals("a2", (vm.state.value as GalaxyUiState.Ready).selectedStarId)
    }

    @Test
    fun setRange_whenTheWindowHasNoStars_showsEmptyInsteadOfAnError() = runTest(dispatcher) {
        coEvery { sv.load(any(), Long.MIN_VALUE, Long.MAX_VALUE) } returns graph
        coEvery { sv.load(any(), 1L, 2L) } returns GalaxyGraph.EMPTY
        val vm = vm()
        advanceUntilIdle()

        vm.setRange(1L, 2L)
        advanceUntilIdle()

        assertEquals(GalaxyUiState.Empty, vm.state.value)
    }

    @Test
    fun aNewerLoad_cancelsTheOlderOne_soStaleDataNeverWins() = runTest(dispatcher) {
        val slow = CompletableDeferred<GalaxyGraph>()
        coEvery { sv.load(any(), Long.MIN_VALUE, Long.MAX_VALUE) } coAnswers { slow.await() }
        coEvery { sv.load(any(), 150L, 250L) } returns GalaxyGraph(listOf(graph.stars[1]), emptyList())
        val vm = vm()
        advanceUntilIdle() // first load is parked on `slow`

        vm.setRange(150L, 250L)
        advanceUntilIdle()
        slow.complete(graph)
        advanceUntilIdle()

        val ready = vm.state.value as GalaxyUiState.Ready
        assertEquals(listOf("a2"), ready.graph.stars.map { it.id })
    }
}
