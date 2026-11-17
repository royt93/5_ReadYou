package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DID_ADDRESS = "bsky:did:plc:abc"
private const val FIRST_ACCOUNT = 1
private const val SECOND_ACCOUNT = 2

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedDaoSourceUrlTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
        .allowMainThreadQueries().build()

    @Before
    fun groups() = runBlocking {
        db.groupDao().insert(Group(id = "g1", name = "one", accountId = FIRST_ACCOUNT), Group(id = "g2", name = "two", accountId = SECOND_ACCOUNT))
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun feed(id: String, url: String, sourceUrl: String?, accountId: Int = FIRST_ACCOUNT) = Feed(
        id = id, name = "name $id", url = url, groupId = if (accountId == FIRST_ACCOUNT) "g1" else "g2", accountId = accountId,
        sourceType = Feed.BLUESKY_SOURCE_TYPE, sourceUrl = sourceUrl,
    )

    @Test fun findsTheSourceWhoseSourceUrlMatches() = runBlocking {
        db.feedDao().insert(feed("f1", "bsky:old-handle", DID_ADDRESS))
        val found = db.feedDao().queryBySourceUrl(FIRST_ACCOUNT, DID_ADDRESS)
        assertEquals(listOf("f1"), found.map { it.id })
        assertEquals("bsky:old-handle", found.single().url)
    }

    @Test fun doesNotMatchOnUrl() = runBlocking {
        db.feedDao().insert(feed("f1", "bsky:old-handle", DID_ADDRESS))
        assertTrue(db.feedDao().queryBySourceUrl(FIRST_ACCOUNT, "bsky:old-handle").isEmpty())
    }

    @Test fun doesNotCrossAccounts() = runBlocking {
        db.feedDao().insert(feed("f2", "bsky:other", DID_ADDRESS, accountId = SECOND_ACCOUNT))
        assertTrue(db.feedDao().queryBySourceUrl(FIRST_ACCOUNT, DID_ADDRESS).isEmpty())
        assertEquals(listOf("f2"), db.feedDao().queryBySourceUrl(SECOND_ACCOUNT, DID_ADDRESS).map { it.id })
    }

    @Test fun isExactNotPartial() = runBlocking {
        db.feedDao().insert(feed("f1", "bsky:x", "bsky:did:plc:abc123"))
        assertTrue(db.feedDao().queryBySourceUrl(FIRST_ACCOUNT, DID_ADDRESS).isEmpty())
    }

    @Test fun isCaseSensitive() = runBlocking {
        db.feedDao().insert(feed("f1", "bsky:x", "bsky:did:plc:AbC"))
        assertTrue(db.feedDao().queryBySourceUrl(FIRST_ACCOUNT, DID_ADDRESS).isEmpty())
    }

    @Test fun aNullSourceUrlNeverMatches() = runBlocking {
        db.feedDao().insert(feed("f1", "bsky:x", null))
        assertTrue(db.feedDao().queryBySourceUrl(FIRST_ACCOUNT, DID_ADDRESS).isEmpty())
        assertTrue(db.feedDao().queryBySourceUrl(FIRST_ACCOUNT, "").isEmpty())
    }
}
