package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncPackageValidatorTest {
    private val sourceId = "a".repeat(32)
    private fun valid() = """{"version":1,"sourceId":"$sourceId","createdAt":1,"feeds":[{"url":"https://a.test/rss","name":"News","groupName":"World"}],"articles":[{"feedUrl":"https://a.test/rss","link":"https://a.test/story","isUnread":false,"isStarred":true,"readAt":10}]}""".toByteArray()
    private fun assertInvalid(bytes: ByteArray) {
        assertTrue(SyncPackageCodec.decodeAndValidate(bytes) is SyncPackageResult.Invalid)
    }

    @Test fun acceptsV1AndNormalizesFeedUrl() {
        val result = SyncPackageCodec.decodeAndValidate(valid()) as SyncPackageResult.Valid
        assertEquals(1, result.data.version)
        assertEquals("https://a.test/rss", result.data.feeds.single().url)
        assertEquals("https://a.test/rss", result.data.articles.single().feedUrl)
    }

    @Test fun rejectsWrongVersionSourceIdAndNegativeTimestamp() {
        val json = String(valid())
        listOf(json.replace("\"version\":1", "\"version\":2"), json.replace(sourceId, "bad"),
            json.replace("\"createdAt\":1", "\"createdAt\":-1")).forEach { assertInvalid(it.toByteArray()) }
    }

    @Test fun rejectsExtraForbiddenAndUnknownKeys() {
        val json = String(valid())
        listOf("title", "content", "apiKey", "password", "accountId", "articleId", "fullContent",
            "rawDescription", "aiSummary", "notes", "unknown").forEach { key ->
            assertInvalid(json.replace("\"version\":1", "\"version\":1,\"$key\":\"x\"").toByteArray())
        }
    }

    @Test fun rejectsMissingRequiredNullAndWrongTypes() {
        val json = String(valid())
        listOf(json.replace(",\"articles\":[", ",\"oops\":["),
            json.replace("\"isUnread\":false", "\"isUnread\":null"),
            json.replace("\"readAt\":10", "\"readAt\":\"ten\"")).forEach { assertInvalid(it.toByteArray()) }
    }

    @Test fun rejectsDuplicateNormalizedFeedUrls() {
        val json = String(valid()).replace("],\"articles\"", ",{\"url\":\"https://A.test/rss/#fragment\",\"name\":\"Again\",\"groupName\":\"Other\"}],\"articles\"")
        assertInvalid(json.toByteArray())
    }

    @Test fun rejectsDuplicateFeedUrlAndLinkArticleKeys() {
        val json = String(valid()).replace("\"readAt\":10}]}", "\"readAt\":10},{\"feedUrl\":\"https://a.test/rss\",\"link\":\"https://a.test/story\",\"isUnread\":true,\"isStarred\":false,\"readAt\":null}]}")
        assertInvalid(json.toByteArray())
    }

    @Test fun rejectsArticleWhoseFeedIsNotInPackage() {
        val json = String(valid()).replace("https://a.test/rss\",\"link", "https://other.test/rss\",\"link")
        assertInvalid(json.toByteArray())
    }

    @Test fun rejectsBadUrlsUserInfoAndControls() {
        val json = String(valid())
        listOf(json.replace("https://a.test/rss", "file:///etc/passwd"),
            json.replace("https://a.test/rss", "https://u:p@a.test/rss"),
            json.replace("\"News\"", "\"Bad\\nName\"")).forEach { assertInvalid(it.toByteArray()) }
    }

    @Test fun rejectsBadJsonAndPackageByteCap() {
        assertInvalid("{".toByteArray())
        val result = SyncPackageCodec.decodeAndValidate(ByteArray(SyncLimits.MAX_ENVELOPE_BYTES + 1))
        assertEquals(SyncPackageError.TOO_LARGE, (result as SyncPackageResult.Invalid).reason)
    }

    @Test fun rejectsArticleCountCapBeforeMapping() {
        val articles = (0..SyncLimits.MAX_ARTICLE_STATES).joinToString(",") {
            "{\"feedUrl\":\"https://a.test/rss\",\"link\":\"https://a.test/$it\",\"isUnread\":true,\"isStarred\":false,\"readAt\":null}"
        }
        assertInvalid("""{"version":1,"sourceId":"$sourceId","createdAt":1,"feeds":[{"url":"https://a.test/rss","name":"n","groupName":"g"}],"articles":[$articles]}""".toByteArray())
    }

    @Test fun rejectsFeedCountCapBeforeMapping() {
        val feeds = (0..SyncLimits.MAX_FEEDS).joinToString(",") {
            "{\"url\":\"https://h$it.test/rss\",\"name\":\"n\",\"groupName\":\"g\"}"
        }
        assertInvalid("""{"version":1,"sourceId":"$sourceId","createdAt":1,"feeds":[$feeds],"articles":[]}""".toByteArray())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncPackageCodecTest {
    @Test fun roundTripEncodesOnlyAllowlistedFields() {
        val input = SyncPackage(1, "a".repeat(32), 10,
            listOf(SyncFeed("https://a.test/rss", "A", "G")),
            listOf(SyncArticleState("https://a.test/rss", "https://a.test/x", false, true, 3)))
        val bytes = SyncPackageCodec.encode(input)
        val decoded = (SyncPackageCodec.decodeAndValidate(bytes) as SyncPackageResult.Valid).data
        assertEquals(input, decoded)
        val raw = bytes.toString(Charsets.UTF_8)
        listOf("apiKey", "password", "accountId", "fullContent", "rawDescription", "aiSummary", "DataStore").forEach {
            assertFalse(raw.contains(it))
        }
    }

    @Test fun fieldOrderAndEncodingAreDeterministic() {
        val input = SyncPackage(1, "a".repeat(32), 1, emptyList(), emptyList())
        assertArrayEquals(SyncPackageCodec.encode(input), SyncPackageCodec.encode(input))
    }
}
