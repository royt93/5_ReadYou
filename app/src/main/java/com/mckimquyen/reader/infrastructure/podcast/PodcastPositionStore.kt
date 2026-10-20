package com.mckimquyen.reader.infrastructure.podcast

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
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
        val array = JSONArray()
        entries.entries.toList().takeLast(MAX_REMEMBERED_POSITIONS).forEach {
            array.put(JSONObject().put(KEY_ID, it.key).put(KEY_POSITION, it.value))
        }
        prefs.edit().remove(LEGACY_KEY_ENTRIES).putString(KEY_ENTRIES_JSON, array.toString()).apply()
    }

    @Synchronized
    fun load(articleId: String): Int = read()[articleId] ?: 0

    private fun read(): LinkedHashMap<String, Int> {
        val result = LinkedHashMap<String, Int>()
        val json = prefs.getString(KEY_ENTRIES_JSON, null)
        if (json != null) {
            runCatching {
                val array = JSONArray(json)
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    result[item.getString(KEY_ID)] = item.getInt(KEY_POSITION)
                }
            }
            return result
        }
        // Positions saved by the first version: "id:sec,id:sec". Ids that contain a delimiter cannot be recovered.
        prefs.getString(LEGACY_KEY_ENTRIES, null).orEmpty().split(LEGACY_ENTRY_SEPARATOR).forEach { entry ->
            val index = entry.lastIndexOf(LEGACY_PAIR_SEPARATOR)
            val seconds = if (index > 0) entry.substring(index + 1).toIntOrNull() else null
            if (seconds != null) result[entry.substring(0, index)] = seconds
        }
        return result
    }

    companion object {
        const val MAX_REMEMBERED_POSITIONS = 50
        const val COMPLETED_TAIL_SEC = 15
        const val PREFS_NAME = "podcast_positions"
        private const val KEY_ENTRIES_JSON = "entries_json"
        private const val KEY_ID = "id"
        private const val KEY_POSITION = "pos"
        private const val LEGACY_KEY_ENTRIES = "entries"
        private const val LEGACY_ENTRY_SEPARATOR = ","
        private const val LEGACY_PAIR_SEPARATOR = ":"
    }
}
