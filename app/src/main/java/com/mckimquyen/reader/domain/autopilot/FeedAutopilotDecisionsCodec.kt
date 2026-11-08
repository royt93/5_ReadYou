package com.mckimquyen.reader.domain.autopilot

import org.json.JSONException
import org.json.JSONObject

/**
 * Stores the user's per-feed Autopilot decisions as one JSON object in a single DataStore string:
 * `{"<feedId>": {"type": "SNOOZE_30_DAYS", "decidedAtMillis": 1794052800000}}`.
 *
 * Decoding never throws: corrupt data yields an empty map so a bad write can never crash the app or the worker.
 */
object FeedAutopilotDecisionsCodec {

    private const val KEY_TYPE = "type"
    private const val KEY_DECIDED_AT = "decidedAtMillis"
    private const val SNOOZE_MILLIS = 30L * 86_400_000L

    fun encode(decisions: Map<String, FeedDecisionRecord>): String {
        val root = JSONObject()
        decisions.forEach { (feedId, record) ->
            root.put(
                feedId,
                JSONObject()
                    .put(KEY_TYPE, record.type.name)
                    .put(KEY_DECIDED_AT, record.decidedAtMillis),
            )
        }
        return root.toString()
    }

    /**
     * @param validFeedIds when not null, entries for feeds that no longer exist are dropped, so deleting a feed
     * anywhere in the app never leaves orphaned decisions behind.
     */
    fun decode(json: String?, nowMillis: Long, validFeedIds: Set<String>?): Map<String, FeedDecisionRecord> {
        if (json.isNullOrBlank()) return emptyMap()
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, FeedDecisionRecord>()
        for (feedId in root.keys()) {
            if (validFeedIds != null && feedId !in validFeedIds) continue
            val entry = root.optJSONObject(feedId) ?: continue
            val type = entry.optString(KEY_TYPE).let { name -> FeedDecisionType.values().firstOrNull { it.name == name } } ?: continue
            if (!entry.has(KEY_DECIDED_AT)) continue
            val decidedAt = entry.optLong(KEY_DECIDED_AT, Long.MIN_VALUE)
            if (decidedAt == Long.MIN_VALUE) continue
            val record = FeedDecisionRecord(type, decidedAt)
            if (type == FeedDecisionType.SNOOZE_30_DAYS && nowMillis - decidedAt >= SNOOZE_MILLIS) continue
            result[feedId] = record
        }
        return result
    }
}
