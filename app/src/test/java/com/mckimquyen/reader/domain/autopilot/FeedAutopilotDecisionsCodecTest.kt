package com.mckimquyen.reader.domain.autopilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FeedAutopilotDecisionsCodecTest {

    private val now = 1_794_052_800_000L
    private val day = 86_400_000L

    @Test fun roundTripEncodeAndDecodePreservesBothDecisionTypes() {
        val original = mapOf(
            "feed_1" to FeedDecisionRecord(FeedDecisionType.SNOOZE_30_DAYS, now - 5 * day),
            "feed_2" to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now - 10 * day),
        )
        val json = FeedAutopilotDecisionsCodec.encode(original)
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, validFeedIds = setOf("feed_1", "feed_2"))
        assertEquals(original, restored)
    }

    @Test fun emptyMapProducesEmptyObject() {
        val json = FeedAutopilotDecisionsCodec.encode(emptyMap())
        assertEquals("{}", json)
        assertEquals(emptyMap<String, FeedDecisionRecord>(), FeedAutopilotDecisionsCodec.decode(json, now, null))
    }

    @Test fun nullOrBlankInputDecodesToEmptyMapWithoutThrowing() {
        assertEquals(emptyMap<String, FeedDecisionRecord>(), FeedAutopilotDecisionsCodec.decode(null, now, null))
        assertEquals(emptyMap<String, FeedDecisionRecord>(), FeedAutopilotDecisionsCodec.decode("", now, null))
        assertEquals(emptyMap<String, FeedDecisionRecord>(), FeedAutopilotDecisionsCodec.decode("   ", now, null))
    }

    @Test fun corruptJsonDecodesToEmptyMapSafely() {
        assertEquals(emptyMap<String, FeedDecisionRecord>(), FeedAutopilotDecisionsCodec.decode("not a json string", now, null))
        assertEquals(emptyMap<String, FeedDecisionRecord>(), FeedAutopilotDecisionsCodec.decode("{corrupt", now, null))
    }

    @Test fun jsonArrayInsteadOfObjectDecodesToEmptyMap() {
        assertEquals(emptyMap<String, FeedDecisionRecord>(), FeedAutopilotDecisionsCodec.decode("[]", now, null))
    }

    @Test fun entryWithUnknownTypeIsIgnoredSafely() {
        val json = """{"f1":{"type":"UNKNOWN_FUTURE_DECISION","decidedAtMillis":$now}}"""
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, null)
        assertTrue(restored.isEmpty())
    }

    @Test fun entryWithMissingOrMalformedFieldsIsIgnored() {
        val json = """{"f1":{"type":"SNOOZE_30_DAYS"},"f2":{"decidedAtMillis":$now},"f3":"invalid"}"""
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, null)
        assertTrue(restored.isEmpty())
    }

    @Test fun snoozeExpiredPastThirtyDaysIsAutomaticallyOmittedOnDecode() {
        val decisions = mapOf(
            "fresh" to FeedDecisionRecord(FeedDecisionType.SNOOZE_30_DAYS, now - 29 * day),
            "expired" to FeedDecisionRecord(FeedDecisionType.SNOOZE_30_DAYS, now - 31 * day),
        )
        val json = FeedAutopilotDecisionsCodec.encode(decisions)
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, null)
        assertEquals(setOf("fresh"), restored.keys)
    }

    @Test fun snoozeAtExactlyThirtyDaysIsExpired() {
        val decisions = mapOf(
            "exact" to FeedDecisionRecord(FeedDecisionType.SNOOZE_30_DAYS, now - 30 * day),
        )
        val json = FeedAutopilotDecisionsCodec.encode(decisions)
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, null)
        assertTrue(restored.isEmpty())
    }

    @Test fun keepForeverNeverExpiresEvenAfterAUserSpecifiedTime() {
        val decisions = mapOf(
            "kept_long_ago" to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now - 365 * day),
        )
        val json = FeedAutopilotDecisionsCodec.encode(decisions)
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, null)
        assertEquals(1, restored.size)
        assertEquals(FeedDecisionType.KEEP_FOREVER, restored["kept_long_ago"]?.type)
    }

    @Test fun orphanFeedIdsArePrunedWhenValidFeedIdsAreSupplied() {
        val decisions = mapOf(
            "existing_feed" to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now),
            "deleted_feed" to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now),
        )
        val json = FeedAutopilotDecisionsCodec.encode(decisions)
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, validFeedIds = setOf("existing_feed"))
        assertEquals(setOf("existing_feed"), restored.keys)
    }

    @Test fun nullValidFeedIdsSkipsOrphanPruning() {
        val decisions = mapOf(
            "any_feed" to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now),
        )
        val json = FeedAutopilotDecisionsCodec.encode(decisions)
        val restored = FeedAutopilotDecisionsCodec.decode(json, now, validFeedIds = null)
        assertEquals(setOf("any_feed"), restored.keys)
    }
}
