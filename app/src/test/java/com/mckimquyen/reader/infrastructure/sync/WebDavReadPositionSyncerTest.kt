package com.mckimquyen.reader.infrastructure.sync

import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavReadPositionSyncerTest {

    @Test
    fun merge_preservesRemotePositionsAndAddsLocalPositions() {
        val remote = listOf(
            WebDavPositionRecord(FEED_URL, ARTICLE_LINK_A, REMOTE_PROGRESS, OLDER_TIMESTAMP),
        )
        val local = listOf(
            position(articleId = ARTICLE_ID_A, articleLink = ARTICLE_LINK_A, progress = LOCAL_PROGRESS),
            position(articleId = ARTICLE_ID_B, articleLink = ARTICLE_LINK_B, progress = SECOND_PROGRESS),
        )

        val merged = WebDavReadPositionSyncer.merge(local, remote)

        assertEquals(2, merged.size)
        assertEquals(REMOTE_PROGRESS, merged.single { it.articleLink == ARTICLE_LINK_A }.progress)
        assertEquals(SECOND_PROGRESS, merged.single { it.articleLink == ARTICLE_LINK_B }.progress)
    }

    @Test
    fun merge_prefersFurtherRemoteProgressAndUsesTimestampWhenProgressMatches() {
        val local = listOf(
            position(articleId = ARTICLE_ID_A, articleLink = ARTICLE_LINK_A, progress = LOCAL_PROGRESS),
            position(articleId = ARTICLE_ID_B, articleLink = ARTICLE_LINK_B, progress = SECOND_PROGRESS),
        )
        val remote = listOf(
            WebDavPositionRecord(FEED_URL, ARTICLE_LINK_A, REMOTE_PROGRESS, OLDER_TIMESTAMP),
            WebDavPositionRecord(FEED_URL, ARTICLE_LINK_B, SECOND_PROGRESS, NEWER_TIMESTAMP),
        )

        val merged = WebDavReadPositionSyncer.merge(local, remote)

        assertEquals(REMOTE_PROGRESS, merged.single { it.articleLink == ARTICLE_LINK_A }.progress)
        assertEquals(NEWER_TIMESTAMP, merged.single { it.articleLink == ARTICLE_LINK_B }.lastReadTime)
    }

    @Test
    fun serializeAndDeserialize_preservesPortableFields() {
        val records = listOf(WebDavPositionRecord(FEED_URL, ARTICLE_LINK_A, REMOTE_PROGRESS, NEWER_TIMESTAMP))

        val json = WebDavReadPositionSyncer.serialize(records)
        val restored = WebDavReadPositionSyncer.deserialize(json)

        assertEquals(records, restored)
        assertTrue(json.contains("feedUrl"))
        assertTrue(json.contains("articleLink"))
        assertFalseLocalIdentifiersPresent(json)
    }

    private fun assertFalseLocalIdentifiersPresent(json: String) {
        assertTrue(!json.contains("articleId"))
        assertTrue(!json.contains("accountId"))
    }

    private fun position(articleId: String, articleLink: String, progress: Float) = ArticleReadPosition(
        articleId = articleId,
        accountId = ACCOUNT_ID,
        feedUrl = FEED_URL,
        articleLink = articleLink,
        progress = progress,
        lastReadTime = OLDER_TIMESTAMP,
        isSynced = false,
    )

    private companion object {
        const val FEED_URL = "https://example.com/feed"
        const val ARTICLE_LINK_A = "https://example.com/article-a"
        const val ARTICLE_LINK_B = "https://example.com/article-b"
        const val ARTICLE_ID_A = "local-account-1-article-a"
        const val ARTICLE_ID_B = "local-account-1-article-b"
        const val ACCOUNT_ID = 1
        const val LOCAL_PROGRESS = 0.35f
        const val REMOTE_PROGRESS = 0.75f
        const val SECOND_PROGRESS = 0.5f
        const val OLDER_TIMESTAMP = 1_700_000_000_000L
        const val NEWER_TIMESTAMP = 1_800_000_000_000L
    }
}
