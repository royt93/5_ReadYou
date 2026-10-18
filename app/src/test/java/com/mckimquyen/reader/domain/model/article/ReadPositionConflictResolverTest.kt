package com.mckimquyen.reader.domain.model.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadPositionConflictResolverTest {

    @Test
    fun resolve_returnsRemoteWhenLocalIsMissing() {
        val remote = position(progress = REMOTE_PROGRESS)

        assertEquals(remote, ReadPositionConflictResolver.resolve(null, remote))
    }

    @Test
    fun resolve_returnsLocalWhenRemoteIsMissing() {
        val local = position(progress = LOCAL_PROGRESS)

        assertEquals(local, ReadPositionConflictResolver.resolve(local, null))
    }

    @Test
    fun resolve_returnsNullWhenBothPositionsAreMissing() {
        assertNull(ReadPositionConflictResolver.resolve(null, null))
    }

    @Test
    fun resolve_prefersRemoteWhenRemoteProgressIsFurther() {
        val local = position(progress = LOCAL_PROGRESS, lastReadTime = OLDER_TIMESTAMP)
        val remote = position(progress = REMOTE_PROGRESS, lastReadTime = NEWER_TIMESTAMP)

        assertEquals(remote, ReadPositionConflictResolver.resolve(local, remote))
    }

    @Test
    fun resolve_prefersLocalWhenLocalProgressIsFurtherEvenIfRemoteIsNewer() {
        val local = position(progress = REMOTE_PROGRESS, lastReadTime = OLDER_TIMESTAMP)
        val remote = position(progress = LOCAL_PROGRESS, lastReadTime = NEWER_TIMESTAMP)

        assertEquals(local, ReadPositionConflictResolver.resolve(local, remote))
    }

    @Test
    fun resolve_usesNewestTimestampWhenProgressMatches() {
        val local = position(progress = LOCAL_PROGRESS, lastReadTime = OLDER_TIMESTAMP)
        val remote = position(progress = LOCAL_PROGRESS, lastReadTime = NEWER_TIMESTAMP)

        assertEquals(remote, ReadPositionConflictResolver.resolve(local, remote))
    }

    private fun position(progress: Float, lastReadTime: Long = OLDER_TIMESTAMP) = ArticleReadPosition(
        articleId = ARTICLE_ID,
        accountId = ACCOUNT_ID,
        feedUrl = FEED_URL,
        articleLink = ARTICLE_LINK,
        progress = progress,
        lastReadTime = lastReadTime,
        isSynced = false,
    )

    private companion object {
        const val ARTICLE_ID = "article-id"
        const val ACCOUNT_ID = 1
        const val FEED_URL = "https://example.com/feed"
        const val ARTICLE_LINK = "https://example.com/article"
        const val LOCAL_PROGRESS = 0.4f
        const val REMOTE_PROGRESS = 0.7f
        const val OLDER_TIMESTAMP = 1_700_000_000_000L
        const val NEWER_TIMESTAMP = 1_800_000_000_000L
    }
}
