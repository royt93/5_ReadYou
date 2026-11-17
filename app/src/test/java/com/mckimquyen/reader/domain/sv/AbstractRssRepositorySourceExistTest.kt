package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CURRENT_ACCOUNT = 7
private const val OTHER_ACCOUNT = 8
private const val DID_ADDRESS = "bsky:did:plc:abc"

/** The real repository wrapper on a real in-memory Room: it must look at the CURRENT account only, and at sourceUrl, not url. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AbstractRssRepositorySourceExistTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
    private var originalAccountId = 1
    private lateinit var service: LocalRssSv // built in @Before, after the account is set

    @Before
    fun setUp() = runBlocking {
        originalAccountId = context.currentAccountId
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, CURRENT_ACCOUNT)
        db.groupDao().insert(Group("g-current", "current", CURRENT_ACCOUNT), Group("g-other", "other", OTHER_ACCOUNT))
        service = LocalRssSv(
            context = context, articleDao = mockk(relaxed = true), feedDao = db.feedDao(), rssHelper = mockk(relaxed = true),
            notificationHelper = mockk(relaxed = true), accountDao = mockk(relaxed = true), groupDao = db.groupDao(),
            feedHealthDao = mockk(relaxed = true), ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
            workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true), smartFilterManager = mockk(relaxed = true),
            webFeedScraper = mockk(), smartCollectionMatcher = mockk(relaxed = true), widgetRefreshNotifier = mockk(relaxed = true),
            retractionRadar = mockk(relaxed = true), nostrRelayClient = mockk(relaxed = true), blueskyApiClient = mockk(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, originalAccountId)
    }

    private fun feed(id: String, accountId: Int, sourceUrl: String?) = Feed(
        id = id, name = id, url = "bsky:old-handle", groupId = if (accountId == CURRENT_ACCOUNT) "g-current" else "g-other",
        accountId = accountId, sourceType = Feed.BLUESKY_SOURCE_TYPE, sourceUrl = sourceUrl,
    )

    @Test fun aSourceOfTheCurrentAccount_isFound() = runBlocking {
        db.feedDao().insert(feed("f1", CURRENT_ACCOUNT, DID_ADDRESS))
        assertTrue(service.isFeedSourceExist(DID_ADDRESS))
    }

    @Test fun aSourceOfAnotherAccount_isNotFound() = runBlocking {
        db.feedDao().insert(feed("f2", OTHER_ACCOUNT, DID_ADDRESS))
        assertFalse(service.isFeedSourceExist(DID_ADDRESS))
    }

    @Test fun theShownAddressIsNotTheSyncAddress() = runBlocking {
        db.feedDao().insert(feed("f1", CURRENT_ACCOUNT, DID_ADDRESS))
        assertFalse(service.isFeedSourceExist("bsky:old-handle"))
    }
}
