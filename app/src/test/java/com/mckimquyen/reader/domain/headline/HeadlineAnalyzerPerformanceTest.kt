package com.mckimquyen.reader.domain.headline

import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlineAnalyzerPerformanceTest {

    @Test
    fun warmAnalysisOf150HeadlinesHasMedianBelow50Milliseconds() {
        val titles = (0 until ARTICLE_COUNT).map { index ->
            when (index % LANGUAGE_COUNT) {
                0 -> "Council $index announces a new transport route after public review"
                1 -> "Sự thật chấn động: hội đồng vừa công bố tuyến xe điện mới số $index"
                2 -> "La verdad impactante sobre la nueva ruta de transporte número $index"
                3 -> "La vérité incroyable sur le nouveau trajet de transport numéro $index"
                else -> "Die unglaubliche Wahrheit über die neue Verkehrsroute Nummer $index"
            }
        }
        // Warm up JIT and lazy regex/lexicon paths before recording measured batches.
        repeat(WARMUP_ROUNDS) { titles.forEach(HeadlineAnalyzer::analyze) }
        val samples = LongArray(MEASURE_ROUNDS) {
            val startedAt = System.nanoTime()
            titles.forEach(HeadlineAnalyzer::analyze)
            System.nanoTime() - startedAt
        }.sorted()
        val medianMillis = samples[samples.size / 2] / NANOS_PER_MILLISECOND
        assertTrue("150-title warm batch median was ${medianMillis}ms", medianMillis < MAX_MEDIAN_MILLIS)
    }

    private companion object {
        const val ARTICLE_COUNT = 150
        const val LANGUAGE_COUNT = 5
        const val WARMUP_ROUNDS = 5
        const val MEASURE_ROUNDS = 9
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_MEDIAN_MILLIS = 50L
    }
}
