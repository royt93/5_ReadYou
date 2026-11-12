package com.mckimquyen.reader.ui.page.home.read.deepdive

import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import javax.inject.Singleton

class DeepDiveCacheTest {

    private fun report(id: String) = DeepDiveReport(id, listOf("a", "b", "c"), emptyList(), emptyList())

    @Test fun missIsNull_hitReturnsTheSameReport() {
        val cache = DeepDiveCache()
        assertNull(cache.get("x"))
        val r = report("x")
        cache.put("x", r)
        assertSame(r, cache.get("x"))
    }

    @Test fun defaultCapacityIsTen() {
        assertEquals(10, DeepDiveCache.DEFAULT_CAPACITY)
        val cache = DeepDiveCache()
        (1..11).forEach { cache.put("a$it", report("a$it")) }
        assertEquals(10, cache.size)
        assertNull("the oldest entry is evicted", cache.get("a1"))
        assertNotNull(cache.get("a11"))
    }

    @Test fun readingAnEntryProtectsItFromEviction() {
        val cache = DeepDiveCache(capacity = 2)
        cache.put("a", report("a"))
        cache.put("b", report("b"))
        cache.get("a")
        cache.put("c", report("c"))
        assertNotNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test fun putTwiceKeepsOneEntryWithTheNewValue() {
        val cache = DeepDiveCache(capacity = 3)
        cache.put("a", report("a"))
        val newer = report("a")
        cache.put("a", newer)
        assertEquals(1, cache.size)
        assertSame(newer, cache.get("a"))
    }

    @Test fun capacityBelowOneIsTreatedAsOne() {
        val cache = DeepDiveCache(capacity = 0)
        cache.put("a", report("a"))
        cache.put("b", report("b"))
        assertEquals(1, cache.size)
    }

    @Test fun cacheIsAHiltSingleton_soItOutlivesTheViewModel() {
        assertNotNull(
            "without @Singleton every opened article would start with an empty cache",
            DeepDiveCache::class.java.getAnnotation(Singleton::class.java),
        )
    }
}
