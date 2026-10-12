package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.digest.DailyDigest
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyDigestPersistenceIntegrationTest {
    private lateinit var database: AndroidDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun digestHistory_isScopedToAccountAndOrderedNewestFirst() = runBlocking {
        database.groupDao().insert(Group(id = "g1", name = "News", accountId = 1))
        database.feedDao().insert(Feed(id = "f1", name = "Feed", url = "https://example.test/rss", groupId = "g1", accountId = 1))
        val older = DailyDigest("d1", 100L, "Older", "{}", "[]", 1)
        val newer = DailyDigest("d2", 200L, "Newer", "{}", "[]", 1)
        database.dailyDigestDao().insert(older)
        database.dailyDigestDao().insert(newer)
        database.dailyDigestDao().insert(DailyDigest("other", 300L, "Other account", "{}", "[]", 2))

        val history = database.dailyDigestDao().queryRecent(accountId = 1)

        assertEquals(listOf("d2", "d1"), history.map { it.id })
        assertEquals("Newer", database.dailyDigestDao().queryById("d2", accountId = 1)?.headline)
        assertNotNull(database.feedDao().queryById("f1"))
    }
}
