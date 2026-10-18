package com.mckimquyen.reader.ui.page.home.read

import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.infrastructure.pref.EInkModePref
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Budget hồi quy của phần E-Ink; không thay thế Macrobenchmark/Perfetto trên toàn app. */
class EInkPerformanceQaTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun repeatedPageTurnsStayWithinBudgetAndDoNotGrowHeapAfterGc() {
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        lateinit var scopeHolder: kotlinx.coroutines.CoroutineScope
        composeRule.setContent {
            CompositionLocalProvider(LocalEInkMode provides EInkModePref.ON) {
                AppTheme(useDarkTheme = false) {
                    state = rememberLazyListState()
                    scopeHolder = rememberCoroutineScope()
                    LazyColumn(Modifier.fillMaxSize().testTag(ROOT).eInkTapZones(true, "perf") { direction ->
                        scopeHolder.launch { state.turnPage(direction) }
                    }, state = state) {
                        items((0 until ITEM_COUNT).toList(), key = { it }) { Box(Modifier.height(ITEM_HEIGHT)) { Text("row-$it") } }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        repeat(WARMUP_TURNS) { tap(right = true) }
        composeRule.runOnUiThread { kotlinx.coroutines.runBlocking { state.scrollToItem(0) } }
        composeRule.waitForIdle()
        gc()
        val heapBefore = usedHeap()
        val samples = ArrayList<Long>(MEASURED_TURNS)
        repeat(MEASURED_TURNS) {
            val start = System.nanoTime()
            tap(right = true)
            composeRule.waitForIdle()
            samples += System.nanoTime() - start
            if (state.firstVisibleItemIndex >= ITEM_COUNT - SAFE_TAIL_ITEMS) {
                composeRule.runOnUiThread { kotlinx.coroutines.runBlocking { state.scrollToItem(0) } }
                composeRule.waitForIdle()
            }
        }
        gc()
        val growth = usedHeap() - heapBefore
        val sorted = samples.sorted()
        val medianMs = sorted[sorted.size / 2] / NANOS_PER_MS
        val p95Ms = sorted[(sorted.size * P95_NUMERATOR / P95_DENOMINATOR).coerceAtMost(sorted.lastIndex)] / NANOS_PER_MS
        println("EINK-PERF turns=$MEASURED_TURNS median=${"%.2f".format(medianMs)}ms p95=${"%.2f".format(p95Ms)}ms heapGrowth=${growth / BYTES_PER_KB}KB")
        assertTrue("median tap-to-idle ${medianMs}ms vượt $MEDIAN_BUDGET_MS", medianMs < MEDIAN_BUDGET_MS)
        assertTrue("p95 tap-to-idle ${p95Ms}ms vượt $P95_BUDGET_MS", p95Ms < P95_BUDGET_MS)
        assertTrue("Heap sau GC tăng ${growth / BYTES_PER_KB}KB sau $MEASURED_TURNS lần lật", growth < HEAP_GROWTH_BUDGET_BYTES)
    }

    @Test
    fun enteringAndLeavingEInkRepeatedlyRecomposesWithoutLeakingComposition() {
        var enabled by mutableStateOf(false)
        val recompositions = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalEInkMode provides if (enabled) EInkModePref.ON else EInkModePref.OFF) {
                AppTheme(useDarkTheme = true) { recompositions.incrementAndGet(); Box(Modifier.testTag(ROOT)) }
            }
        }
        composeRule.waitForIdle()
        gc()
        val before = usedHeap()
        val start = System.nanoTime()
        repeat(TOGGLES) { enabled = !enabled; composeRule.waitForIdle() }
        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MS
        gc()
        val growth = usedHeap() - before
        println("EINK-PERF toggles=$TOGGLES total=${elapsedMs}ms recompositions=${recompositions.get()} heapGrowth=${growth / BYTES_PER_KB}KB")
        assertEquals("Recomposition phải theo đúng số lần đổi mode", TOGGLES + 1, recompositions.get())
        assertTrue("Toggle E-Ink quá chậm: ${elapsedMs}ms", elapsedMs < TOGGLE_BUDGET_MS)
        assertTrue("Heap tăng ${growth / BYTES_PER_KB}KB sau toggles", growth < HEAP_GROWTH_BUDGET_BYTES)
    }

    private fun tap(right: Boolean) {
        composeRule.onNodeWithTag(ROOT).performTouchInput { click(if (right) centerRight else centerLeft) }
    }

    private fun gc() {
        repeat(GC_ROUNDS) { Runtime.getRuntime().gc(); System.runFinalization(); Thread.sleep(GC_PAUSE_MS) }
    }

    private fun usedHeap(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } + Debug.getNativeHeapAllocatedSize()

    private companion object {
        const val ROOT = "eink-perf-root"
        const val ITEM_COUNT = 1_000
        val ITEM_HEIGHT = 96.dp
        const val WARMUP_TURNS = 8
        const val MEASURED_TURNS = 80
        const val SAFE_TAIL_ITEMS = 20
        const val TOGGLES = 40
        const val NANOS_PER_MS = 1_000_000.0
        const val MEDIAN_BUDGET_MS = 250.0
        const val P95_BUDGET_MS = 500.0
        const val TOGGLE_BUDGET_MS = 6_000.0
        const val HEAP_GROWTH_BUDGET_BYTES = 8L * 1024 * 1024
        const val BYTES_PER_KB = 1024
        const val GC_ROUNDS = 3
        const val GC_PAUSE_MS = 80L
        const val P95_NUMERATOR = 95
        const val P95_DENOMINATOR = 100
    }
}
