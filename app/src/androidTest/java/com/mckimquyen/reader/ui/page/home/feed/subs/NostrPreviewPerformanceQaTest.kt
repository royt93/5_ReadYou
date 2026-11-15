package com.mckimquyen.reader.ui.page.home.feed.subs

import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Date

/**
 * Frame time and heap budget for the subscribe preview, driven by code (no touch): the preview content is composed,
 * swapped between a short and a long list many times, and removed again. It measures only this screen, not the whole
 * app. On the Tecno KJ7 (2026-11-15) the median was about 54 ms per swap and `dumpsys gfxinfo` called about 50% of frames
 * janky; a control that swaps plain `Text` rows with no app code gave about 60 ms and 52%, so that jank belongs to the
 * phone, not to this screen. The budgets below only catch a regression well beyond that.
 */
class NostrPreviewPerformanceQaTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun article(i: Int, title: String) =
        Article("a$i", Date(i.toLong()), title, rawDescription = "", shortDescription = "", link = "https://njump.me/note1$i", feedId = "f", accountId = 1)

    @Test
    fun swappingThePreviewManyTimes_staysWithinTheFrameBudget_andDoesNotGrowTheHeap() {
        val npub = requireNotNull(NostrKeys.hexToNpub("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"))
        var count by mutableStateOf(1)
        var visible by mutableStateOf(true)
        composeRule.setContent {
            AppTheme(useDarkTheme = false) {
                if (visible) {
                    WebFeedPreviewContent(
                        siteTitle = NostrFeedMapper.feedName(npub),
                        articles = (1..count).map { article(it, "Note number $it with some words to wrap onto a second line of text") },
                        onConfirm = {}, onCancel = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        repeat(WARMUP) { step(it, { count = if (it % 2 == 0) 20 else 3 }) }
        gc()
        val heapBefore = usedHeap()
        val samples = ArrayList<Long>(MEASURED)
        repeat(MEASURED) { i ->
            val start = System.nanoTime()
            composeRule.runOnUiThread { count = if (i % 2 == 0) 20 else 3 }
            composeRule.waitForIdle()
            samples += System.nanoTime() - start
        }
        composeRule.runOnUiThread { visible = false }
        composeRule.waitForIdle()
        gc()
        val growth = usedHeap() - heapBefore
        val sorted = samples.sorted()
        val medianMs = sorted[sorted.size / 2] / NANOS_PER_MS.toDouble()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / NANOS_PER_MS.toDouble()
        println("NOSTR-PREVIEW-PERF swaps=$MEASURED median=${"%.2f".format(medianMs)}ms p95=${"%.2f".format(p95Ms)}ms heapGrowth=${growth / BYTES_PER_KB}KB")
        assertTrue("median swap-to-idle ${medianMs}ms is over $MEDIAN_BUDGET_MS ms", medianMs < MEDIAN_BUDGET_MS)
        assertTrue("p95 swap-to-idle ${p95Ms}ms is over $P95_BUDGET_MS ms", p95Ms < P95_BUDGET_MS)
        assertTrue("heap after GC grew ${growth / BYTES_PER_KB} KB over $MEASURED swaps", growth < HEAP_GROWTH_BUDGET_BYTES)
    }

    private fun step(i: Int, change: (Int) -> Unit) {
        composeRule.runOnUiThread { change(i) }
        composeRule.waitForIdle()
    }

    private fun gc() {
        repeat(GC_ROUNDS) { Runtime.getRuntime().gc(); System.runFinalization(); Thread.sleep(GC_PAUSE_MS) }
    }

    /** Java heap in use plus the native heap the process has allocated (bitmaps and text layout live there). */
    private fun usedHeap(): Long =
        Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() + Debug.getNativeHeapAllocatedSize()

    private companion object {
        const val WARMUP = 10
        const val MEASURED = 200
        const val NANOS_PER_MS = 1_000_000L
        const val BYTES_PER_KB = 1024L
        const val MEDIAN_BUDGET_MS = 120L
        const val P95_BUDGET_MS = 400L
        const val HEAP_GROWTH_BUDGET_BYTES = 6L * 1024 * 1024
        const val GC_ROUNDS = 3
        const val GC_PAUSE_MS = 50L
    }
}
