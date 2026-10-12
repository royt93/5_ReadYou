package com.mckimquyen.reader.domain.model.bilingual

import java.io.Closeable

/**
 * Small thread-safe LRU of closeable resources: asking for an existing key reuses it, and when [capacity] is
 * exceeded the least recently used one is closed. Keeps native handles (ML Kit translators) bounded.
 */
class ClosableLruPool<K : Any, V : Closeable>(
    private val capacity: Int,
    private val create: (K) -> V,
) {
    private val map = LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    val size: Int @Synchronized get() = map.size

    @Synchronized
    fun get(key: K): V {
        map[key]?.let { return it }
        val value = create(key)
        map[key] = value
        while (map.size > capacity) {
            val eldest = map.entries.first()
            map.remove(eldest.key)
            closeQuietly(eldest.value)
        }
        return value
    }

    @Synchronized
    fun closeAll() {
        map.values.forEach(::closeQuietly)
        map.clear()
    }

    private fun closeQuietly(value: Closeable) {
        try {
            value.close()
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Releasing one resource must not stop the others from being released.
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 8
        const val LOAD_FACTOR = 0.75f
    }
}
