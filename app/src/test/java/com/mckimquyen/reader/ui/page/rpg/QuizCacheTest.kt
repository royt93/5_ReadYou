package com.mckimquyen.reader.ui.page.rpg

import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCacheTest {

    private fun quiz(id: String) = QuizQuestion(id, "q", listOf("a", "b", "c", "d"), 0, "e", "c")

    @Test
    fun missReturnsNull_hitReturnsTheSameQuiz() {
        val cache = QuizCache()
        assertNull(cache.get("a"))

        cache.put("a", quiz("a"))

        assertEquals(quiz("a"), cache.get("a"))
    }

    @Test
    fun defaultCapacityIsTwenty_andTheOldestUnusedEntryIsEvictedFirst() {
        val cache = QuizCache()
        (1..20).forEach { cache.put("a$it", quiz("a$it")) }
        assertEquals(20, cache.size)

        cache.put("a21", quiz("a21"))

        assertEquals(20, cache.size)
        assertNull("the oldest entry must go", cache.get("a1"))
        assertNotNull(cache.get("a2"))
        assertNotNull(cache.get("a21"))
    }

    @Test
    fun readingAnEntryKeepsItAlive() {
        val cache = QuizCache(capacity = 3)
        cache.put("a", quiz("a")); cache.put("b", quiz("b")); cache.put("c", quiz("c"))
        cache.get("a") // a is now the most recently used

        cache.put("d", quiz("d"))

        assertNotNull("a was just used", cache.get("a"))
        assertNull("b was the least recently used", cache.get("b"))
    }

    @Test
    fun puttingTheSameIdTwice_replacesAndDoesNotGrow() {
        val cache = QuizCache(capacity = 2)
        cache.put("a", quiz("a"))
        cache.put("a", quiz("a").copy(question = "new"))

        assertEquals(1, cache.size)
        assertEquals("new", cache.get("a")?.question)
    }

    @Test
    fun aZeroOrNegativeCapacityIsTreatedAsOne() {
        val cache = QuizCache(capacity = 0)
        cache.put("a", quiz("a")); cache.put("b", quiz("b"))

        assertEquals(1, cache.size)
        assertNotNull(cache.get("b"))
    }

    @Test
    fun theCacheIsAHiltSingleton_soItOutlivesTheViewModelsTheReaderKeepsCreating() {
        assertTrue(
            "without @Singleton each view model gets its own empty cache and the same article calls Gemini again",
            QuizCache::class.java.isAnnotationPresent(javax.inject.Singleton::class.java),
        )
    }
}
