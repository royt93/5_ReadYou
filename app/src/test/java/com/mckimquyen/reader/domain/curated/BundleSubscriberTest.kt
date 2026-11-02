package com.mckimquyen.reader.domain.curated

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class BundleSubscriberTest {

    private class FakeGateway(
        private val failing: Set<String> = emptySet(),
        private val perFeedDelayMillis: Long = 0L,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : BundleFeedGateway {
        val createdGroups = mutableListOf<String>()
        val deletedGroups = mutableListOf<String>()
        val subscribed = mutableListOf<String>()
        private val active = AtomicInteger(0)
        var maxActive = 0

        override suspend fun createGroup(name: String): String {
            createdGroups += name
            return "group-${createdGroups.size}"
        }

        override suspend fun deleteGroup(groupId: String) {
            deletedGroups += groupId
        }

        override suspend fun subscribeFeed(source: CuratedSource, groupId: String) {
            val now = active.incrementAndGet()
            synchronized(this) { maxActive = maxOf(maxActive, now) }
            try {
                gate?.await()
                if (perFeedDelayMillis > 0) delay(perFeedDelayMillis)
                if (source.url in failing) throw IOException("boom ${source.url}")
                synchronized(this) { subscribed += source.url }
            } finally {
                active.decrementAndGet()
            }
        }
    }

    private fun source(i: Int) = CuratedSource("S$i", "https://s$i.example/rss")

    private fun bundle(count: Int) = CuratedBundle(
        id = "b", titleEn = "Bundle", titleVi = "Bộ", verifiedOn = "2026-11-02",
        sources = (1..count).map { source(it) },
    )

    private suspend fun run(
        gateway: FakeGateway,
        bundle: CuratedBundle,
        existing: Set<String> = emptySet(),
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) = BundleSubscriber(gateway).subscribe(bundle, existing, "Group name", onProgress)

    @Test
    fun subscribe_addsEverySource_inOneNewGroup() = runTest {
        val gateway = FakeGateway()

        val result = run(gateway, bundle(3))

        assertEquals(BundleSubscribeResult(added = 3, skippedExisting = 0, failed = 0), result)
        assertEquals(listOf("Group name"), gateway.createdGroups)
        assertEquals(3, gateway.subscribed.size)
        assertTrue(gateway.deletedGroups.isEmpty())
    }

    @Test
    fun subscribe_skipsSourcesTheAccountAlreadyHas_withoutFetchingThem() = runTest {
        val gateway = FakeGateway()

        val result = run(gateway, bundle(3), existing = setOf("https://S1.example/rss/"))

        assertEquals(BundleSubscribeResult(added = 2, skippedExisting = 1, failed = 0), result)
        assertTrue("https://s1.example/rss" !in gateway.subscribed)
    }

    @Test
    fun subscribe_oneFailingFeedDoesNotStopTheOthers() = runTest {
        val gateway = FakeGateway(failing = setOf("https://s2.example/rss"))

        val result = run(gateway, bundle(3))

        assertEquals(BundleSubscribeResult(added = 2, skippedExisting = 0, failed = 1), result)
        assertTrue(gateway.deletedGroups.isEmpty())
    }

    @Test
    fun subscribe_whenEveryFeedFails_removesTheEmptyGroup() = runTest {
        val gateway = FakeGateway(failing = (1..3).map { source(it).url }.toSet())

        val result = run(gateway, bundle(3))

        assertEquals(BundleSubscribeResult(added = 0, skippedExisting = 0, failed = 3), result)
        assertEquals(listOf("group-1"), gateway.deletedGroups)
    }

    @Test
    fun subscribe_whenEverythingAlreadyExists_neverCreatesAGroup() = runTest {
        val gateway = FakeGateway()

        val result = run(gateway, bundle(2), existing = setOf("https://s1.example/rss", "https://s2.example/rss"))

        assertEquals(BundleSubscribeResult(added = 0, skippedExisting = 2, failed = 0), result)
        assertTrue(gateway.createdGroups.isEmpty())
    }

    @Test
    fun subscribe_emptyBundle_returnsZerosAndCreatesNothing() = runTest {
        val gateway = FakeGateway()

        val result = run(gateway, bundle(0))

        assertEquals(BundleSubscribeResult(0, 0, 0), result)
        assertTrue(gateway.createdGroups.isEmpty())
    }

    @Test
    fun subscribe_neverRunsMoreThanMaxParallelFeedsAtOnce() = runTest {
        val gateway = FakeGateway(perFeedDelayMillis = 10)

        val result = run(gateway, bundle(10))

        assertEquals(10, result.added)
        assertTrue("đồng thời tối đa ${gateway.maxActive}", gateway.maxActive <= BundleSubscriber.MAX_PARALLEL_FEEDS)
        assertTrue("phải thực sự chạy song song", gateway.maxActive > 1)
    }

    @Test
    fun subscribe_reportsProgressUpToTheTotal() = runTest {
        val gateway = FakeGateway()
        val seen = mutableListOf<Pair<Int, Int>>()

        run(gateway, bundle(3)) { done, total -> synchronized(seen) { seen += done to total } }

        assertEquals(3 to 3, seen.last())
        assertEquals(listOf(1, 2, 3), seen.map { it.first }.sorted())
    }

    @Test
    fun subscribe_cancelledMidway_rethrowsAndRemovesTheEmptyGroup() = runTest {
        val gateway = FakeGateway(gate = CompletableDeferred())
        var finished = false
        val job = launch {
            run(gateway, bundle(3))
            finished = true
        }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue("không được báo xong khi đã huỷ", !finished)
        assertEquals(listOf("group-1"), gateway.deletedGroups)
    }
}
