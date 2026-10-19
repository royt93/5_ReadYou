package com.mckimquyen.reader.infrastructure.podcast

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the last listening position (seconds) per article; most recently saved kept last. */
@Singleton
class PodcastPositionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(articleId: String, positionSec: Int, durationSec: Int?) {
        val finished = durationSec != null && durationSec > 0 && positionSec >= durationSec - COMPLETED_TAIL_SEC
        val stored = if (finished) 0 else positionSec.coerceAtLeast(0)
        val entries = read().filterKeys { it != articleId }.toMutableMap()
        entries[articleId] = stored
        val trimmed = entries.entries.toList().takeLast(MAX_REMEMBERED_POSITIONS)
        prefs.edit().putString(KEY_ENTRIES, trimmed.joinToString(ENTRY_SEPARATOR) { "${it.key}$PAIR_SEPARATOR${it.value}" }).apply()
    }

    @Synchronized
    fun load(articleId: String): Int = read()[articleId] ?: 0

    private fun read(): LinkedHashMap<String, Int> {
        val result = LinkedHashMap<String, Int>()
        val raw = prefs.getString(KEY_ENTRIES, null).orEmpty()
        if (raw.isEmpty()) return result
        raw.split(ENTRY_SEPARATOR).forEach { entry ->
            val index = entry.lastIndexOf(PAIR_SEPARATOR)
            val seconds = if (index > 0) entry.substring(index + 1).toIntOrNull() else null
            if (seconds != null) result[entry.substring(0, index)] = seconds
        }
        return result
    }

    companion object {
        const val MAX_REMEMBERED_POSITIONS = 50
        const val COMPLETED_TAIL_SEC = 15
        const val PREFS_NAME = "podcast_positions"
        private const val KEY_ENTRIES = "entries"
        private const val ENTRY_SEPARATOR = ","
        private const val PAIR_SEPARATOR = ":"
    }
}
