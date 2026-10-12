package com.mckimquyen.reader.domain.model.bilingual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

class ClosableLruPoolTest {

    private class Res(val name: String) : Closeable {
        var closed = false
        override fun close() { closed = true }
    }

    @Test
    fun sameKey_reusesTheInstance_andCreatesOnlyOnce() {
        var created = 0
        val pool = ClosableLruPool<String, Res>(capacity = 2) { created++; Res(it) }
        assertSame(pool.get("a"), pool.get("a"))
        assertEquals(1, created)
    }

    @Test
    fun overCapacity_closesTheLeastRecentlyUsed_andOnlyThat() {
        val pool = ClosableLruPool<String, Res>(capacity = 2) { Res(it) }
        val a = pool.get("a")
        val b = pool.get("b")
        pool.get("a") // a is now the most recent
        val c = pool.get("c") // evicts b

        assertTrue("b was least recently used", b.closed)
        assertFalse(a.closed)
        assertFalse(c.closed)
        assertEquals(2, pool.size)
    }

    @Test
    fun anEvictedKey_isRecreatedFresh() {
        val pool = ClosableLruPool<String, Res>(capacity = 1) { Res(it) }
        val first = pool.get("a")
        pool.get("b")
        val again = pool.get("a")
        assertTrue(first.closed)
        assertFalse(again.closed)
        assertTrue(first !== again)
    }

    @Test
    fun closeAll_closesEverything_andEmptiesThePool() {
        val pool = ClosableLruPool<String, Res>(capacity = 3) { Res(it) }
        val items = listOf("a", "b", "c").map(pool::get)
        pool.closeAll()
        assertTrue(items.all { it.closed })
        assertEquals(0, pool.size)
    }

    @Test
    fun aResourceThatThrowsOnClose_doesNotStopTheRestFromClosing() {
        val bad = object : Closeable { override fun close() = error("boom") }
        val good = Res("good")
        val pool = ClosableLruPool<String, Closeable>(capacity = 3) { if (it == "bad") bad else good }
        pool.get("bad"); pool.get("good")
        pool.closeAll()
        assertTrue(good.closed)
    }
}
