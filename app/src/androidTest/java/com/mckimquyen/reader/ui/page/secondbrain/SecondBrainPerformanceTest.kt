package com.mckimquyen.reader.ui.page.secondbrain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.secondbrain.BrainArticle
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraphBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: the graph must be ready in under 1 second with up to 200 nodes, measured on a real device. */
@RunWith(AndroidJUnit4::class)
class SecondBrainPerformanceTest {
    @Test
    fun build_and_layout_500Articles_200Nodes_underOneSecond() {
        val articles = (1..500).map { n ->
            BrainArticle("a$n", "Title $n", n * 3_600_000L, setOf("c${n % 250}", "c${(n * 7) % 250}", "hub"))
        }
        BrainGraphLayout.compute(BrainGraphBuilder.build(articles)) // warm up JIT

        val start = System.nanoTime()
        val graph = BrainGraphBuilder.build(articles)
        val positions = BrainGraphLayout.compute(graph)
        val ms = (System.nanoTime() - start) / 1_000_000

        assertEquals(BrainGraphBuilder.MAX_NODES, graph.nodes.size)
        assertEquals(graph.nodes.size, positions.size)
        println("SecondBrain build+layout 500 articles/200 nodes: ${ms}ms")
        assertTrue("took ${ms}ms, spec limit 1000ms", ms < 1_000)
    }
}
