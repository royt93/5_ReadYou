package com.mckimquyen.reader.ui.component.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec: converting text must take under 50 ms so the page never stutters.
 *
 * Measures only the Bionic work (`withBionic`) on a real device, per paragraph and for a whole long
 * article, NOT total compose/layout time (which is dominated by Compose text layout and unrelated to Bionic).
 */
@RunWith(AndroidJUnit4::class)
class BionicPerformanceTest {

    private fun paragraph(words: Int) =
        AnnotatedString(("Lorem ipsum dolor sit amet consectetur adipiscing elit sed do ".repeat(words / 10 + 1)).trim())

    private inline fun medianMs(runs: Int = 9, block: () -> Unit): Double {
        repeat(3) { block() } // warm up JIT
        val samples = (1..runs).map {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        return samples[samples.size / 2]
    }

    @Test
    fun onePagesWorthOfText_underFiftyMilliseconds() {
        val page = paragraph(300) // ~ one screen of prose
        val ms = medianMs { page.withBionic(true) }
        println("BIONIC-PERF page(${page.length} chars): ${"%.2f".format(ms)}ms")
        assertTrue("one page took ${ms}ms, spec < 50ms", ms < 50.0)
    }

    @Test
    fun wholeLongArticle_inParagraphs_stillUnderFiftyMillisecondsTotal() {
        val paragraphs = List(120) { paragraph(60) } // ~7k words
        val total = paragraphs.sumOf { it.length }
        val ms = medianMs { paragraphs.forEach { it.withBionic(true) } }
        println("BIONIC-PERF article($total chars, ${paragraphs.size} paragraphs): ${"%.2f".format(ms)}ms")
        assertTrue("whole article took ${ms}ms, spec < 50ms", ms < 50.0)
    }

    @Test
    fun disabled_isEffectivelyFree() {
        val page = paragraph(300)
        val ms = medianMs { repeat(1_000) { page.withBionic(false) } }
        println("BIONIC-PERF disabled x1000: ${"%.2f".format(ms)}ms")
        assertTrue("1000 disabled calls took ${ms}ms", ms < 5.0)
    }
}
